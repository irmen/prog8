; Bare-metal display demo: plain old ECS 640x256x5 hires screen (32 colors).
; The custom.compute_display helper computes shadow registers, which are
; patched into a copper list (blitcop-style) that programs the display
; every frame. A small CPU palette is required, otherwise everything
; renders black (the color registers are never touched by the copper).

%import exec
%import graphics
%import custom

main {
    const word WIDTH = 640
    const word HEIGHT = 256
    const ubyte DEPTH = 4

    ; Chip-RAM bitmap backing the display; the shadow registers live in the
    ; custom.compute_display returns a pointer to them).
    ^^graphics.BitMap bm
    ^^custom.DisplayRegs regs

    sub start() {
        if setup_bitmap() == false {
            return
        }
        regs = custom.compute_display(WIDTH, HEIGHT, DEPTH, custom.DISPLAY_HIRES, 0)
        if regs == 0 {
            free_bitmap()
            return
        }
        build_copperlist()
        custom.grab_system(true)
        custom.waitvsync()
        ; Activate the copper list: it programs the display registers and
        ; reloads the bitplane pointers every frame by itself.
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_COPPER
        custom.COPJMP1 = 0
        setup_palette()
        paint_stuff()
        repeat 100 {
            custom.waitvsync()
        }
        custom.FMODE = $0000      ; back to 16-bit fetch before the OS takes over
        custom.restore_system()
        free_bitmap()
    }

    sub build_copperlist() {
        ; Patch the shadow register values into the copper list below.
        copperdata.copper_list[35] = regs.bplcon0
        copperdata.copper_list[37] = regs.diwstrt
        copperdata.copper_list[39] = regs.diwstop
        copperdata.copper_list[41] = regs.ddfstrt
        copperdata.copper_list[43] = regs.ddfstop
        copperdata.copper_list[45] = regs.fmode
        copperdata.copper_list[47] = regs.bpl1mod
        copperdata.copper_list[49] = regs.bpl2mod
        for p in 0 to DEPTH - 1 {
            copperdata.copper_list[3 + p * 4] = msw(bm.Planes[p])
            copperdata.copper_list[5 + p * 4] = lsw(bm.Planes[p])
        }
    }

    sub setup_palette() {
        ; 32 distinct colors so every grid cell is distinguishable.
        for c in 1 to 15 {
            ubyte r = ((c as uword) * 8) as ubyte
            ubyte g = (((c * 37) as uword) & 255) as ubyte
            ubyte b = (((c * 97) as uword) & 255) as ubyte
            custom.set_aga_color(c as ubyte, ((r as long) << 16) | ((g as long) << 8) | (b as long))
        }
    }

    sub setup_bitmap() -> bool {
        bm = exec.AllocMem(sizeof(graphics.BitMap), exec.MEMF_CLEAR)
        if bm == 0
            return false
        graphics.InitBitMap(bm, DEPTH as byte, WIDTH, HEIGHT)
        for p in 0 to DEPTH - 1 {
            bm.Planes[p] = graphics.AllocRaster(WIDTH as uword, HEIGHT as uword)
            if bm.Planes[p] == 0
                return false
        }
        return true
    }

    sub free_bitmap() {
        for p in 0 to bm.Depth - 1 {
            graphics.FreeRaster(bm.Planes[p], bm.BytesPerRow * 8, bm.Rows)
        }
        exec.FreeMem(bm, sizeof(graphics.BitMap))
    }

    sub paint_stuff() {
        ; Fill an 80x32 grid of 8x8 cells (640x256 pixels) so every scanline
        ; is drawn up to and including the 256th line.
        uword bpr = bm.BytesPerRow
        for row in 0 to 31 {
            for col in 0 to 79 {
                ubyte color = ((row * 80 + col) & 15) as ubyte
                uword base = (row as uword) * 8 * bpr + (col as uword) + (row as uword)
                for p in 0 to bm.Depth - 1 {
                    ^^ubyte plane = bm.Planes[p] as ^^ubyte
                    ubyte fill = 0
                    if (color & ($01 << p)) != 0
                        fill = $ff
                    for dy in 0 to 6 {
                        plane[base + (dy as uword) * bpr] = fill
                    }
                }
            }
        }
    }
}

copperdata {
    %option amiga_chipram

    ; Raw copper list words: register offset, value per MOVE.
    ; The BPLCON0/DIW/DDF/FMODE/MOD values and the bitplane pointers are
    ; patched in by build_copperlist(); plane slots beyond DEPTH stay zero.
    ; DMACON enables raster DMA; copper DMA itself is enabled up front and
    ; the list ends with CEND so the copper restarts from COP1LC each frame.
    uword[52] copper_list = [
        $0096, $8300,    ; MOVE DMACON: SET | DMAEN | BPLEN
        $00e0, 0, $00e2, 0,    ; MOVE BPL1PT (patched)
        $00e4, 0, $00e6, 0,    ; MOVE BPL2PT (patched)
        $00e8, 0, $00ea, 0,    ; MOVE BPL3PT (patched)
        $00ec, 0, $00ee, 0,    ; MOVE BPL4PT (patched)
        $00f0, 0, $00f2, 0,    ; MOVE BPL5PT (patched)
        $00f4, 0, $00f6, 0,    ; MOVE BPL6PT (patched)
        $00f8, 0, $00fa, 0,    ; MOVE BPL7PT (patched)
        $00fc, 0, $00fe, 0,    ; MOVE BPL8PT (patched)
        $0100, 0,    ; MOVE BPLCON0 (patched)
        $008e, 0,    ; MOVE DIWSTRT (patched)
        $0090, 0,    ; MOVE DIWSTOP (patched)
        $0092, 0,    ; MOVE DDFSTRT (patched)
        $0094, 0,    ; MOVE DDFSTOP (patched)
        $01fc, 0,    ; MOVE FMODE (patched)
        $0108, 0,    ; MOVE BPL1MOD (patched)
        $010a, 0,    ; MOVE BPL2MOD (patched)
        $ffff, $fffe     ; END
    ]
}
