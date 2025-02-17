%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        cx16.r5 = one()
        txt.print_uw(cx16.r5)
        txt.nl()

        cx16.r5, cx16.r6 = two()
        txt.print_uw(cx16.r5)
        txt.spc()
        txt.print_uw(cx16.r6)
        txt.nl()

        cx16.r5, cx16.r6, cx16.r7 = three()
        txt.print_uw(cx16.r5)
        txt.spc()
        txt.print_uw(cx16.r6)
        txt.spc()
        txt.print_uw(cx16.r7)
        txt.nl()

        ; bytes
        cx16.r5L = oneb()
        txt.print_ub(cx16.r5L)
        txt.nl()

        cx16.r5L, cx16.r6L = twob()
        txt.print_ub(cx16.r5L)
        txt.spc()
        txt.print_ub(cx16.r6L)
        txt.nl()

        cx16.r5L, cx16.r6L, cx16.r7L = threeb()
        txt.print_ub(cx16.r5L)
        txt.spc()
        txt.print_ub(cx16.r6L)
        txt.spc()
        txt.print_ub(cx16.r7L)
        txt.nl()
    }

    uword @shared w1=1111
    uword @shared w2=2222
    uword @shared w3=3333
    ubyte @shared b1=11
    ubyte @shared b2=22
    ubyte @shared b3=33

    sub one() -> uword {
        cx16.r0L++
        return w1
    }

    sub two() -> uword, uword {         ; TODO no P8SRC generated in IR?
        return w1, w2
    }

    sub three() -> uword, uword, uword {         ; TODO no P8SRC generated in IR?
        return w1, w2, w3
    }

    sub oneb() -> ubyte {
        cx16.r0L++
        return b1
    }

    sub twob() -> ubyte,ubyte {         ; TODO no P8SRC generated in IR?
        return b1, b2
    }

    sub threeb() -> ubyte, ubyte, ubyte {         ; TODO no P8SRC generated in IR?
        return b1, b2, b3
    }
}
