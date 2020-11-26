%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        float fl
        word ww
        uword uw
        byte bb
        ubyte ub


        fl = 9997.999
        ww = (fl+1.1) as word
        uw = (fl+1.1) as uword
        fl = 97.999
        bb = (fl+1.1) as byte
        ub = (fl+1.1) as ubyte

        txt.print_w(ww)
        txt.chrout('\n')
        txt.print_uw(uw)
        txt.chrout('\n')
        txt.print_b(bb)
        txt.chrout('\n')
        txt.print_ub(ub)
        txt.chrout('\n')


        test_stack.test()

    }
}
