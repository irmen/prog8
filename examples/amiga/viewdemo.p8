; Bare-metal 320x256 lores display with concentric color rectangles.
; The custom.compute_display helper computes shadow registers, which are
; patched into a copper list (blitcop-style) that programs the display
; every frame. A small CPU palette is required, otherwise everything
; renders black (the color registers are never touched by the copper).

%import graphics
%import custom
%import playfield
%import textio

main {
    const word WIDTH = 320
    const word HEIGHT = 256
    const ubyte DEPTH = 4
    const uword RECTANGLE_COUNT = 15
    const uword RECTANGLE_SPACING = 8

    ubyte[8] pixelMasks = [$80, $40, $20, $10, $08, $04, $02, $01]
    ; Chip-RAM bitmap backing the display and its shadow display registers.
    ^^graphics.BitMap bm
    ^^custom.DisplayRegs regs

    sub start() {
        ubyte chipset = custom.detect_chipset()
        when chipset {
            custom.CHIPSET::OCS -> {
                txt.print("Detected chipset: OCS\n")
            }
            custom.CHIPSET::ECS -> {
                txt.print("Detected chipset: ECS\n")
            }
            custom.CHIPSET::AGA -> {
                txt.print("Detected chipset: AGA\n")
            }
        }
        if not playfield.create(WIDTH, HEIGHT, WIDTH, HEIGHT, DEPTH, custom.DISPLAY_DEFAULT, 0)
            return
        bm = playfield.bitmap
        regs = playfield.regs
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
        playfield.destroy()
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
        copperdata.copper_list[53] = regs.diwhigh
        playfield.patch_copper(&copperdata.copper_list, 3)
    }

    sub setup_palette() {
        ; Set the 15 colors used by the rectangle outlines.
        custom.set_color(0, 0)
        for c in 1 to 15 {
            ubyte r = ((c as uword) * 8) as ubyte
            ubyte g = (((c * 37) as uword) & 255) as ubyte
            ubyte b = (((c * 97) as uword) & 255) as ubyte
            custom.set_aga_color(c as ubyte, ((r as long) << 16) | ((g as long) << 8) | (b as long))
        }
    }

    sub paint_stuff() {
        for layer in 0 to RECTANGLE_COUNT - 1 {
            uword inset = layer * RECTANGLE_SPACING
            uword right = (WIDTH - 1) as uword - inset
            uword bottom = (HEIGHT - 1) as uword - inset
            ubyte color = (layer + 1) as ubyte
            draw_hline(inset, right, inset, color)
            draw_hline(inset, right, bottom, color)
            draw_vline(inset, inset, bottom, color)
            draw_vline(right, inset, bottom, color)
        }
    }

    sub draw_hline(uword left, uword right, uword y, ubyte color) {
        for x in left to right
            plot_pixel(x, y, color)
    }

    sub draw_vline(uword x, uword top, uword bottom, ubyte color) {
        for y in top to bottom
            plot_pixel(x, y, color)
    }

    sub plot_pixel(uword x, uword y, ubyte color) {
        uword byteOffset = y * bm.BytesPerRow + (x >> 3)
        ubyte mask = pixelMasks[lsb(x) & 7]
        for p in 0 to DEPTH - 1 {
            pointer address = bm.Planes[p] + byteOffset
            if (color & ($01 << p)) != 0
                @(address) |= mask
            else
                @(address) &= ~mask
        }
    }
}

copperdata {
    %option amiga_chipram

    ; Raw copper list words: register offset, value per MOVE.
    ; The BPLCON0/DIW/DDF/FMODE/MOD values and the bitplane pointers are
    ; patched in by build_copperlist(); control registers are reset explicitly.
    ; DMACON enables raster DMA; copper DMA itself is enabled up front and
    ; the list ends with CEND so the copper restarts from COP1LC each frame.
    uword[58] copper_list = [
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
        $0102, 0,    ; MOVE BPLCON1 (reset fine scrolling)
        $01e4, 0,    ; MOVE DIWHIGH (reset extended window coordinates)
        $0104, 0,    ; MOVE BPLCON2 (single-playfield priorities)
        $ffff, $fffe     ; END
    ]
}
