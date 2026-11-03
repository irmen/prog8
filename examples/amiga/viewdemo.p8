; Bare-metal 320x256 lores display with concentric color rectangles.
; The custom.compute_display helper computes shadow registers, which the
; copper builder emits into a copper list that programs the display
; every frame. A small CPU palette is required, otherwise everything
; renders black (the color registers are never touched by the copper).

%import graphics
%import custom
%import playfield
%import copper
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
        uword planeBase = build_copperlist()
        playfield.patch_copper(copper.list, planeBase)
        custom.grab_system(true)
        custom.waitvsync()
        ; Activate the copper list: it programs the display registers and
        ; reloads the bitplane pointers every frame by itself.
        custom.COP1LC = copper.list
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

    sub build_copperlist() -> uword {
        ; Build the copper list with the copper helper. Returns the base
        ; value-word index of the bitplane pointer slots, ready to pass to
        ; playfield.patch_copper(). DMACON enables raster DMA; copper DMA
        ; itself is enabled up front and the list ends with CEND so the
        ; copper restarts from COP1LC each frame.
        copper.init(&copperdata.copper_list, len(copperdata.copper_list))
        void copper.move($0096, $8300)    ; DMACON: SET | DMAEN | BPLEN
        uword planeBase = copper.attach_planes(DEPTH)
        void copper.move($0100, regs.bplcon0)
        void copper.move($008e, regs.diwstrt)
        void copper.move($0090, regs.diwstop)
        void copper.move($0092, regs.ddfstrt)
        void copper.move($0094, regs.ddfstop)
        void copper.move($01fc, regs.fmode)
        void copper.move($0108, regs.bpl1mod)
        void copper.move($010a, regs.bpl2mod)
        void copper.move($0102, 0)        ; BPLCON1: reset fine scrolling
        void copper.move($01e4, regs.diwhigh)
        void copper.move($0104, 0)        ; BPLCON2: single-playfield priorities
        copper.end()
        return planeBase
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

    ; Word buffer owned by the copper builder (see main.build_copperlist()):
    ; DMACON + 4 planes + 11 register MOVEs + END = 42 words.
    uword[48] copper_list
}
