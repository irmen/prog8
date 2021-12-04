%import textio
%zeropage basicsafe

main {

    ubyte[64*3] palette

    sub start() {
        ubyte i
        for i in 0 to len(palette)-1 {
            palette[i] = 15
        }

        for i in 0 to len(palette)-1 {
            txt.print_ubhex(palette[i], false)
        }
        txt.nl()
        make_ehb_palette()
        for i in 0 to len(palette)-1 {
            txt.print_ubhex(palette[i], false)
        }
        txt.nl()

    }

    sub make_ehb_palette() {
        ; generate 32 additional Extra-Halfbrite colors in the cmap
        uword palletteptr = &palette
        uword ehbptr = palletteptr + 32*3
        repeat 32 {
            @(ehbptr) = @(palletteptr)>>1
            ehbptr++
            palletteptr++
            @(ehbptr) = @(palletteptr)>>1
            ehbptr++
            palletteptr++
            @(ehbptr) = @(palletteptr)>>1
            ehbptr++
            palletteptr++
        }
    }

}
