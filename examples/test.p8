%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv = 99887766
        lv, cx16.r0, cx16.r2L = func(lv)
        txt.print_l(lv)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.spc()
        txt.print_ub(cx16.r2L)
        txt.nl()
    }

    sub func(long arg) -> long, uword, ubyte {
         arg -= 1234567
         txt.print("func: ")
         txt.print_l(arg)
         txt.nl()
         return arg, 9999, 42
    }
}
