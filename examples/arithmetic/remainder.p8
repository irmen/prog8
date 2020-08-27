%import c64textio
%zeropage basicsafe

; TODO implement  REMAINDER asmgeneration


main {

    sub start() {
        remainder_ubyte(0, 1, 0)
        remainder_ubyte(100, 6, 4)
        remainder_ubyte(255, 2, 1)
        remainder_ubyte(255, 20, 15)

        remainder_uword(0,1,0)
        remainder_uword(40000,511,142)
        remainder_uword(40000,500,0)
        remainder_uword(43211,12,11)
    }

    sub remainder_ubyte(ubyte a1, ubyte a2, ubyte c) {
        ubyte r = a1%a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("ubyte ")
        txt.print_ub(a1)
        txt.print(" % ")
        txt.print_ub(a2)
        txt.print(" = ")
        txt.print_ub(r)
        c64.CHROUT('\n')
    }

    sub remainder_uword(uword a1, uword  a2, uword c) {
        uword  r = a1%a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("uword ")
        txt.print_uw(a1)
        txt.print(" % ")
        txt.print_uw(a2)
        txt.print(" = ")
        txt.print_uw(r)
        c64.CHROUT('\n')
    }
}
