%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        uword uw
        ubyte ub

        ub = 10
        uw = ub * 320
        txt.print_uw(uw)
        txt.chrout('\n')

        ub = 100
        uw = ub * 320
        txt.print_uw(uw)
        txt.chrout('\n')

        ub = 200
        uw = ub * 320
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 10
        uw *= 320
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 100
        uw *= 320
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 200
        uw *= 320
        txt.print_uw(uw)
        txt.chrout('\n')
        txt.chrout('\n')

        uw = 0
        ub = 10
        uw = uw + 320*ub
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 0
        ub = 100
        uw = uw + 320*ub
        txt.print_uw(uw)
        txt.chrout('\n')

        uw = 0
        ub = 200
        uw = uw + 320*ub
        txt.print_uw(uw)
        txt.chrout('\n')

        txt.print("hello\n")

    }
}
