%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv = $aabbccdd

        ; TODO fix :  @(&lv as ^^ubyte + 1) = 0

        ubyte b1,b2
        uword w1,w2

        b1 = lsb(lv+$11000011)
        b2 = msb(lv+$11000011)
        w1 = lsw(lv+$11000011)
        w2 = msw(lv+$11000011)

        txt.print_ubhex(b1, true)
        txt.nl()
        txt.print_ubhex(b2, true)
        txt.nl()
        txt.print_uwhex(w1, true)
        txt.nl()
        txt.print_uwhex(w2, true)
        txt.nl()

    }
}
