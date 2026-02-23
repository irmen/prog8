%import textio
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        ubyte @shared b1, b2, b3, b4
        long @shared lv = $11223344
        b1, b2, b3 = lmh(lv)
        cx16.VERA_ADDR_L, cx16.VERA_ADDR_M, cx16.VERA_ADDR_H = lmh(lv)

        txt.print_ubhex(b1, true)
        txt.spc()
        txt.print_ubhex(b2, true)
        txt.spc()
        txt.print_ubhex(b3, true)
        txt.nl()

        b1, b2, b3 = lmh($11aabbcc)
        txt.print_ubhex(b1, true)
        txt.spc()
        txt.print_ubhex(b2, true)
        txt.spc()
        txt.print_ubhex(b3, true)
        txt.nl()
    }
}

