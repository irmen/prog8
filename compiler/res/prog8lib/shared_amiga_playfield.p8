; playfield - bare-metal bitplane manager, shared by the amiga500 and amiga1200 targets.
;
; Kickstart 1.3 compatible: the AllocBitMap fast path is only taken when
; sys.exec_version() reports 3.0+, otherwise InitBitMap + AllocRaster is used.
; Pairs with custom.compute_display(): create()
; allocates chip-RAM bitplanes and computes the shadow display registers,
; show()/patch_copper() install the plane addresses, apply_registers() writes
; the rest. The caller owns the copper list and calls custom.grab_system() /
; custom.restore_system() itself (see examples/amiga/viewdemo.p8).
; No dual playfield, interlace, DBLPAL/DBLNTSC or superhires: whatever
; compute_display() rejects, create() rejects too (returns false).
;
; Single active playfield (module-level state, like easygfx). For double
; buffering, manage a second graphics.BitMap yourself and point the hardware
; at it via show() after swapping playfield.bitmap.
;
; Allocation strategy: on Kickstart 3.0+ graphics.AllocBitMap allocates the
; requested backing dimensions with BMF_CLEAR; older Kickstarts use
; InitBitMap + AllocRaster followed by clear() to get the same zeroed planes.

%import exec
%import graphics
%import custom

playfield {
    %option merge, ignore_unused

    ^^graphics.BitMap bitmap      ; chip-ram bitmap backing the display (0 = none)
    ^^custom.DisplayRegs regs     ; shadow registers from compute_display() (0 = none)

    private word pf_width         ; backing bitmap width (for FreeRaster)
    private word pf_height        ; backing bitmap height (for FreeRaster)
    private uword pf_stride       ; effective bytes per row
    private bool pf_bitmap        ; true when the whole bitmap came from AllocBitMap
    private ^^graphics.BitMap nofriend  ; null friend bitmap for AllocBitMap (stays 0)

    sub create(word view_width, word view_height, word bitmap_width, word bitmap_height, ubyte depth, uword mode, word vertical_offset) -> bool {
        ; Allocate chip-ram bitplanes and compute the shadow display registers.
        ; The viewport and backing bitmap sizes are separate. Both widths must
        ; be multiples of 16, and the bitmap must cover the viewport. Scrolling
        ; the larger bitmap is left to the caller's bitplane pointer updates.
        ; vertical_offset is in scanlines; positive values move the display down.
        ; Returns false if the mode is unsupported or allocation fails, in
        ; which case nothing is left allocated (no destroy() needed).
        destroy()
        pf_bitmap = false
        if depth == 0 or depth > 8
            return false
        if bitmap_width < view_width or (bitmap_width & 15) != 0
            return false
        if bitmap_height < view_height or bitmap_height <= 0
            return false
        regs = custom.compute_display(view_width, view_height, bitmap_width, depth, mode, vertical_offset)
        if regs == 0
            return false
        pf_width = bitmap_width
        pf_height = bitmap_height
        if sys.exec_version() >= 39 {
            ; Kickstart 3.0+: one call allocates struct + displayable chip planes.
            ; BMF_CLEAR|BMF_DISPLAYABLE, no friend bitmap, no interleaving so
            ; Planes[] stays one pointer per plane like the AllocRaster path.
            bitmap = graphics.AllocBitMap(bitmap_width as long, bitmap_height as long, depth as long, graphics.BMF_CLEAR | graphics.BMF_DISPLAYABLE, nofriend)
            if bitmap == 0 {
                regs = 0
                return false
            }
            pf_bitmap = true
            pf_stride = bitmap.BytesPerRow
            return true
        }
        bitmap = exec.AllocMem(sizeof(graphics.BitMap), exec.MEMF_CLEAR)
        if bitmap == 0 {
            regs = 0
            return false
        }
        pf_bitmap = false
        graphics.InitBitMap(bitmap, depth as byte, bitmap_width, bitmap_height)
        pf_stride = bitmap.BytesPerRow
        for p in 0 to depth-1 {
            bitmap.Planes[p] = graphics.AllocRaster(bitmap_width as uword, bitmap_height as uword)
            if bitmap.Planes[p] == 0 {
                destroy()
                return false
            }
        }
        ; AllocRaster leaves chip ram uncleared (unlike AllocBitMap with
        ; BMF_CLEAR), so zero the planes to match the 3.0+ path.
        clear()
        return true
    }

    sub destroy() {
        ; Free the bitplanes and the bitmap itself. Safe to call when no
        ; playfield is active, or after a failed create().
        if bitmap == 0
            return
        if pf_bitmap {
            graphics.FreeBitMap(bitmap)
        } else {
            for p in 0 to bitmap.Depth-1 {
                if bitmap.Planes[p] != 0
                    graphics.FreeRaster(bitmap.Planes[p], pf_width as uword, pf_height as uword)
            }
            exec.FreeMem(bitmap, sizeof(graphics.BitMap))
        }
        bitmap = 0
        regs = 0
    }

    sub clear() {
        ; Zero all bitplanes, row by row (a whole plane may exceed a uword).
        if bitmap == 0
            return
        for p in 0 to bitmap.Depth-1 {
            ^^ubyte plane = bitmap.Planes[p] as ^^ubyte
            for row in 0 to bitmap.Rows-1 {
                sys.memclear(plane + (row as uword)*pf_stride, pf_stride)
            }
        }
    }

    sub show() {
        ; Install the plane addresses into the BPLxPT registers. Call once for
        ; a static display, or every frame after flipping bitmaps.
        if bitmap == 0
            return
        show_planes(bitmap, bitmap.Depth)
    }

    private asmsub show_planes(^^graphics.BitMap bm @A0, ubyte depth @D0) {
        ; The BPLxPT registers are contiguous (4 bytes apart), so one small
        ; loop writes them all: a single move.l per plane, no dispatch.
        %asm {{
            adda.w  #8,a0               ; a0 -> Planes[0] (BytesPerRow 0, Rows 2, Flags 4, Depth 5, Pad 6)
            movea.l #$dff0e0,a1         ; a1 -> BPL1PT
            subq.w  #1,d0
            blt.s   .done
.loop:      move.l  (a0)+,(a1)+
            dbra    d0,.loop
.done:      rts
        }}
    }

    sub apply_registers() {
        ; Write the shadow registers to the hardware (once per display setup).
        ; Plane pointers are NOT written here, use show()/patch_copper().
        ; Scrolling is reset to 0, use set_scroll() afterwards if needed.
        if regs == 0
            return
        custom.DIWSTRT = regs.diwstrt
        custom.DIWSTOP = regs.diwstop
        custom.DIWHIGH = regs.diwhigh
        custom.DDFSTRT = regs.ddfstrt
        custom.DDFSTOP = regs.ddfstop
        custom.BPLCON0 = regs.bplcon0
        custom.BPLCON1 = 0
        custom.BPL1MOD = regs.bpl1mod
        custom.BPL2MOD = regs.bpl2mod
        custom.FMODE = regs.fmode
    }

    sub patch_copper(^^uword copper, uword base) {
        ; Patch plane addresses into copper MOVE value slots: plane p's high
        ; word goes to copper[base + p*4], low word to copper[base+2 + p*4]
        ; (each plane occupies: register, high, register, low). Same layout
        ; as the copper list in examples/amiga/viewdemo.p8.
        if bitmap == 0
            return
        for p in 0 to bitmap.Depth-1 {
            uword slot = base + (p as uword)*4
            copper[slot] = msw(bitmap.Planes[p])
            copper[slot+2] = lsw(bitmap.Planes[p])
        }
    }

    sub set_scroll(ubyte fine) {
        ; Horizontal fine scroll 0-15 (hires-pixel units). Both playfield
        ; halves get the same value because this is a single playfield.
        ; Combine with plane-pointer bumps for full smooth scrolling.
        ubyte s = fine & 15
        custom.BPLCON1 = ((s as uword) << 4) | s
    }
}
