%zeropage basicsafe

main {

    sub start() {

        byte b1
        byte b2
        word w1
        word w2
        ubyte ub1
        ubyte ub2
        uword uw1
        uword uw2

        b1 = 5
        b2= 122
        b1 = b2+b1
        c64scr.print_b(b1)
        c64.CHROUT('\n')

        w1 = -1111
        w2 = 11231
        w1 = w2+w1
        c64scr.print_w(w1)
        c64.CHROUT('\n')

        uw1 = 55555
        uw2 = 1123
        uw1 = uw2+uw1
        c64scr.print_uw(uw1)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }
}
