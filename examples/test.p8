%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        ubyte ub
        uword uw
        float @shared fl1

        ub, uw = multi()
        txt.print_ub(ub)
        txt.spc()
        txt.print_uw(uw)
        txt.nl()

        ub, uw, fl1 = multif()

        txt.print_ub(ub)
        txt.spc()
        txt.print_uw(uw)
        txt.spc()
        txt.print_f(fl1)
        txt.nl()
    }

    sub multi() -> ubyte, uword {
        cx16.r0 = 55
        cx16.r1 = 11111
        return cx16.r0L, cx16.r1
    }

    sub multif() -> ubyte, uword, float {
        cx16.r0 = 111
        cx16.r1 = 22222
        return cx16.r0L, cx16.r1, 42.99
    }
}
