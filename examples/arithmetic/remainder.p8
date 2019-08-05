%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

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
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("ubyte ")
        c64scr.print_ub(a1)
        c64scr.print(" % ")
        c64scr.print_ub(a2)
        c64scr.print(" = ")
        c64scr.print_ub(r)
        c64.CHROUT('\n')
    }

    sub remainder_uword(uword a1, uword  a2, uword c) {
        uword  r = a1%a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("uword ")
        c64scr.print_uw(a1)
        c64scr.print(" % ")
        c64scr.print_uw(a2)
        c64scr.print(" = ")
        c64scr.print_uw(r)
        c64.CHROUT('\n')
    }
}
