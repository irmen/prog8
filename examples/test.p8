%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ubyte u1 = 100
        ubyte u2 = 30
        byte ub = -30

        abs(ub)
        word r = moo(u1,u2)
        c64scr.print_w(r)

    }

    sub moo(ubyte p1, word p2) -> word {
        c64scr.print_ub(p1)
        c64.CHROUT(',')
        c64scr.print_w(p2)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        for word ww in 200 to 300 step 13 {
            c64scr.print_w(ww)
            c64.CHROUT('\n')
        }

        ubyte u1 = 100
        ubyte u2 = 30

        c64scr.print_ub(u1 % u2)
        c64.CHROUT('\n')
        c64scr.print_ub(u1 / u2)
        c64.CHROUT('\n')
        c64scr.print_ub(u2 * 2)
        c64.CHROUT('\n')
        c64scr.print_ub(u2 * 7)
        c64.CHROUT('\n')
        return 12345
    }
}
