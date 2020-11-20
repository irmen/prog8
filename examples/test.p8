%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        ubyte ub
        byte bb
        uword uw
        word ww

        ubyte arrub = 10
        uword arruw = 10
        byte arrb = 10
        word arrw = 10

        test_stack.test()

        for ub in 0 to arrub  step 2 {
            txt.print_ub(ub)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for ub in 5 to arrub step 2 {
            txt.print_ub(ub)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for uw in 0 to arruw  step 2 {
            txt.print_uw(uw)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for uw in 5 to arruw step 2 {
            txt.print_uw(uw)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for bb in 0 to arrb step 2 {
            txt.print_b(bb)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for bb in -2 to arrb-2 step 2 {
            txt.print_b(bb)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for ww in 0 to arrw step 2 {
            txt.print_w(ww)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for ww in -2 to arrw-2 step 2{
            txt.print_w(ww)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for bb in arrb-2 to -2 step -2 {
            txt.print_b(bb)
            txt.chrout(',')
        }
        txt.chrout('\n')

        for ww in arrw-2 to -2 step -2 {
            txt.print_w(ww)
            txt.chrout(',')
        }
        txt.chrout('\n')

        test_stack.test()
        txt.chrout('\n')
    }
}
