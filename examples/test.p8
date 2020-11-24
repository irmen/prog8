%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        uword uw = $c000
        ubyte ub = 1
        ubyte ub2 = 1

        uw = 1000
        uw += ub+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw -= ub+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw *= ub+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw /= ub+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw %= 5*ub+ub2+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw <<= ub+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw >>= ub+ub2
        txt.print_uw(uw)
        txt.chrout('\n')

        test_stack.test()

    }
}
