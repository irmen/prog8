%import textio
%option enable_floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte ub
        uword uw
        ub, uw, fl = multi()
    }

    float @shared fl

    sub multi() -> ubyte, uword, float {
        cx16.r0++
        return cx16.r0L, cx16.r1, fl
    }
}
