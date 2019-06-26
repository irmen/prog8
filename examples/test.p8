%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
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
    }
}
