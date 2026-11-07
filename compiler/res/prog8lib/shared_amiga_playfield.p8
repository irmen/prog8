; playfield - playfield setup and display for bare-metal programs, shared by
; the amiga500 and amiga1200 targets.
;
; Intent: own the bitplanes and display registers of a single playfield for
; code that bangs the hardware directly (games, demos, double-buffered
; animation). The module is deliberately split into two phases:
;
;   setup  - runs while the OS is alive: create() allocates chip-RAM
;            bitplanes through graphics.library and computes the shadow
;            display registers with custom.compute_display();
;            create_backplane() allocates a matching second buffer.
;            This is the only phase that calls the OS; letting Kickstart
;            do the chip-RAM bookkeeping is the reason this module exists.
;   display - needs no OS at all: show() writes the plane addresses into
;            BPLxPT, apply_registers() writes the rest of the shadow
;            registers, patch_copper() patches plane addresses into the
;            caller's copper list. All safe after custom.grab_system(),
;            which the caller performs (and later restore_system()) itself;
;            the caller also owns the copper list
;            (see examples/amiga/viewdemo.p8).
;
; No dual playfield, interlace, DBLPAL/DBLNTSC or superhires: whatever
; compute_display() rejects, create() rejects too (returns false).
;
; Single active playfield (module-level state, like easygfx). Double
; buffering: create_backplane() allocates a matching second buffer and
; flip() swaps the two structs - flip() is pure bookkeeping, the visible
; update still has to happen via show() or patch_copper(). Drawing always
; targets back while bitmap is shown (see examples/amiga/3d.p8).
;
; Kickstart 1.3 compatible. Allocation strategy: on Kickstart 3.0+
; graphics.AllocBitMap allocates the requested backing dimensions with
; BMF_CLEAR; older Kickstarts use InitBitMap + AllocRaster followed by
; clear() to get the same zeroed planes. The back buffer uses the same
; split, so both buffers match on all systems.

%import exec
%import graphics
%import custom

playfield {
    %option merge, ignore_unused

    ^^graphics.BitMap bitmap      ; chip-ram bitmap backing the display (0 = none)
    ^^custom.DisplayRegs regs     ; shadow registers from compute_display() (0 = none)
    ^^graphics.BitMap back        ; second buffer for double buffering (0 = none)

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
        ; Free the bitplanes and the bitmap itself, including the back
        ; buffer. Safe to call when no playfield is active, or after a
        ; failed create().
        free_bitmap(bitmap)
        free_bitmap(back)
        bitmap = 0
        back = 0
        regs = 0
    }

    sub destroy_backplane() {
        ; Free the back buffer only (the front stays active). Safe to
        ; call when there is none.
        free_bitmap(back)
        back = 0
    }

    private sub free_bitmap(^^graphics.BitMap bm) {
        ; Free one bitmap allocated by create()/create_backplane(). No-op on 0.
        if bm == 0
            return
        if pf_bitmap {
            graphics.FreeBitMap(bm)
        } else {
            for p in 0 to bm.Depth-1 {
                if bm.Planes[p] != 0
                    graphics.FreeRaster(bm.Planes[p], pf_width as uword, pf_height as uword)
            }
            exec.FreeMem(bm, sizeof(graphics.BitMap))
        }
    }

    sub create_backplane() -> bool {
        ; Allocate a second chip-ram bitmap with the same geometry as the
        ; active playfield, for double buffering. Drawing targets back
        ; while bitmap is shown; flip() swaps the two. Frees any previous
        ; back buffer first. Returns false when no playfield is active or
        ; the allocation fails (back stays 0, no destroy_backplane() needed).
        destroy_backplane()
        if bitmap == 0
            return false
        if pf_bitmap {
            ; Kickstart 3.0+: struct + displayable chip planes in one call.
            back = graphics.AllocBitMap(pf_width as long, pf_height as long, bitmap.Depth as long, graphics.BMF_CLEAR | graphics.BMF_DISPLAYABLE, nofriend)
            if back == 0
                return false
            return true
        }
        back = exec.AllocMem(sizeof(graphics.BitMap), exec.MEMF_CLEAR)
        if back == 0
            return false
        graphics.InitBitMap(back, bitmap.Depth as byte, pf_width, pf_height)
        for p in 0 to back.Depth-1 {
            back.Planes[p] = graphics.AllocRaster(pf_width as uword, pf_height as uword)
            if back.Planes[p] == 0 {
                destroy_backplane()
                return false
            }
        }
        ; AllocRaster leaves chip ram uncleared (unlike AllocBitMap with
        ; BMF_CLEAR), so zero the planes to match the 3.0+ path.
        clear_backplane()
        return true
    }

    sub flip() {
        ; Swap the front (bitmap) and back buffers for double buffering.
        ; Afterwards install the new front: show() for a register-driven
        ; display, or patch_copper()/copper.set() for a copper-driven one.
        ; No-op when there is no back buffer.
        if back == 0
            return
        ^^graphics.BitMap tmp = bitmap
        bitmap = back
        back = tmp
    }

    private inline sub clear() {
        ; Zero the front bitmap, row by row (a whole plane may exceed a uword).
        ; Setup-time helper for create(); per-frame clears should use
        ; blitter.fill_rect(..., 0, 0) instead.
        clear_bitmap(bitmap)
    }

    private inline sub clear_backplane() {
        ; Zero the back buffer, row by row. Call it on both sides of
        ; flip() to clear both buffers. This is a CPU fill: fine at setup
        ; time, but for per-frame clears prefer blitter.fill_rect(..., 0, 0),
        ; which is faster and runs in parallel with the CPU.
        clear_bitmap(back)
    }

    private sub clear_bitmap(^^graphics.BitMap bm) {
        ; Zero all bitplanes of one bitmap. No-op on 0.
        if bm == 0
            return
        for p in 0 to bm.Depth-1 {
            ^^ubyte plane = bm.Planes[p] as ^^ubyte
            for row in 0 to bm.Rows-1 {
                sys.memclear(plane + (row as uword)*pf_stride, pf_stride)
            }
        }
    }

    sub show() {
        ; Install the plane addresses into the BPLxPT registers. Call once for
        ; a static display, or every frame after flipping bitmaps.
        ; Installs the bitmap origin; for a scrolled viewport use scroll_to().
        if bitmap == 0
            return
        show_planes(bitmap, bitmap.Depth, 0)
    }

    sub scroll_to(uword xpix, uword yline) -> ubyte {
        ; Point a register-driven display at viewport position (xpix, yline)
        ; within a larger backing bitmap. Programs the BPLxPT registers with
        ; the word-rounded address and BPLCON1 with the 0-15 fine remainder,
        ; and returns that fine value: copper-driven displays patch their
        ; own BPLCON1 slot with it (the copper list usually resets BPLCON1),
        ; alongside patch_copper_at() for the pointers. Modulos need no
        ; change (they already span the backing pitch). No-op (returns 0)
        ; when no playfield is active. xpix wraps at word granularity only
        ; in the caller's own clipping; valid range is
        ; 0..backingWidth-viewWidth, 0..backingHeight-viewHeight.
        if bitmap == 0
            return 0
        uword cofs
        ubyte fine
        cofs, fine = scroll_split(xpix, yline)
        show_planes(bitmap, bitmap.Depth, cofs)
        set_scroll(fine)
        return fine
    }

    private inline sub scroll_split(uword xpix, uword yline) -> uword, ubyte {
        ; Split a viewport position into the coarse byte offset (word-rounded,
        ; for plane pointers) and the 0-15 fine value (for BPLCON1). Pure
        ; computation, no state: the single source of truth both installers use.
        return yline * pf_stride + ((xpix >> 3) & $fffe), lsb(xpix) & 15
    }

    private asmsub show_planes(^^graphics.BitMap bm @A0, ubyte depth @D0, uword offset @D1) {
        ; The BPLxPT registers are contiguous (4 bytes apart), so one small
        ; loop writes them all: plane base + offset per plane, no dispatch.
        ; D2 is saved/restored (only D0-D1/A0-A1 are scratch). The depth
        ; arrives in d0.b, so mask the word counter first: a garbage high
        ; byte would spray plane addresses across the whole custom range.
        %asm {{
            adda.w  #8,a0               ; a0 -> Planes[0] (BytesPerRow 0, Rows 2, Flags 4, Depth 5, Pad 6)
            movea.l #$dff0e0,a1         ; a1 -> BPL1PT
            move.l  d2,-(sp)
            and.l   #$0000ffff,d1       ; zero-extend the uword offset (long immediate: no sign trap)
            andi.w  #$00ff,d0           ; zero-extend the ubyte depth (word counter)
            subq.w  #1,d0
            blt.s   .done
.loop:      move.l  (a0)+,d2
            add.l   d1,d2
            move.l  d2,(a1)+
            dbra    d0,.loop
.done:      move.l  (sp)+,d2
            rts
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
        ; DIWHIGH is an ECS/AGA register; on OCS it does not exist and writing
        ; to it corrupts the display. Only touch it on ECS or AGA chipsets.
        if custom.detect_chipset() != custom.CHIPSET::OCS
            custom.DIWHIGH = regs.diwhigh
        custom.DDFSTRT = regs.ddfstrt
        custom.DDFSTOP = regs.ddfstop
        custom.BPLCON0 = regs.bplcon0
        custom.BPLCON1 = 0
        custom.BPLCON2 = 0    ; single-playfield priorities, like the copper lists
        custom.BPL1MOD = regs.bpl1mod
        custom.BPL2MOD = regs.bpl2mod
        custom.FMODE = regs.fmode
    }

    sub patch_copper(^^uword copper, uword base) {
        ; Patch plane addresses into copper MOVE value slots: plane p's high
        ; word goes to copper[base + p*4], low word to copper[base+2 + p*4]
        ; (each plane occupies: register, high, register, low). Same layout
        ; as the copper list in examples/amiga/viewdemo.p8.
        ; Installs the bitmap origin; for a scrolled viewport use
        ; patch_copper_at().
        if bitmap == 0
            return
        for p in 0 to bitmap.Depth-1 {
            uword slot = base + (p as uword)*4
            copper[slot] = msw(bitmap.Planes[p])
            copper[slot+2] = lsw(bitmap.Planes[p])
        }
    }

    sub patch_copper_at(^^uword copper, uword base, uword xpix, uword yline) -> ubyte {
        ; Patch scrolled plane addresses into copper MOVE value slots (same
        ; layout as patch_copper()): the viewport origin within a larger
        ; backing bitmap. Returns the 0-15 fine value to store in the
        ; copper list's BPLCON1 slot. No-op (returns 0) when no playfield
        ; is active.
        if bitmap == 0
            return 0
        uword cofs
        ubyte fine
        cofs, fine = scroll_split(xpix, yline)
        for p in 0 to bitmap.Depth-1 {
            uword slot = base + (p as uword)*4
            pointer addr = bitmap.Planes[p] + cofs
            copper[slot] = msw(addr)
            copper[slot+2] = lsw(addr)
        }
        return fine
    }

    sub set_scroll(ubyte fine) {
        ; Horizontal fine scroll 0-15 (hires-pixel units). Both playfield
        ; halves get the same value because this is a single playfield.
        ; Manual override for the fine part; scroll_to()/patch_copper_at()
        ; manage it together with the coarse plane pointers.
        ubyte s = fine & 15
        custom.BPLCON1 = ((s as uword) << 4) | s
    }

    ; Busy-waits on the beam; a vertical-blank (VERTB) IRQ handler that
    ; paces the loop without spin-waiting is usually better.
    alias waitvsync = custom.waitvsync    ; frame pacer: block until the beam passes line 300
}
