%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        uword uw = $c000
        ubyte ub = 1
        ubyte ub2 = 1
        uword uv1 = 1
        uword uv2 = 1

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

        uw = $1111
        uw &= (ub+ub2) | 15
        txt.print_uwhex(uw, 1)
        txt.chrout('\n')

        uw = $1111
        uw |= (ub+ub2) | 15
        txt.print_uwhex(uw, 1)
        txt.chrout('\n')

        uw = $1111
        uw ^= (ub+ub2) | 15
        txt.print_uwhex(uw, 1)
        txt.chrout('\n')

        txt.chrout('\n')



        uw = 1000
        uw += uv1+uv2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw -= uv1+uv2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw *= uv1+uv2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw /= uv1+uv2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 1000
        uw %= 5*uv1+uv2+uv2
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = $1111
        uw &= (uv1+uv2) | 1023
        txt.print_uwhex(uw, 1)
        txt.chrout('\n')

        uw = $1111
        uw |= (uv1+uv2) | 32768
        txt.print_uwhex(uw, 1)
        txt.chrout('\n')

        uw = $1111
        uw ^= (uv1+uv2) | 32768
        txt.print_uwhex(uw, 1)
        txt.chrout('\n')

        test_stack.test()

    }
}
