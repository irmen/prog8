%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        ubyte[]  ubarray = [1,2,3,4]
        byte[]  barray = [1,2,3,4]
        uword[]  uwarray = [1,2,3,4]
        word[]  warray = [1,2,3,4]
        float[]  farray = [1.1,2.2,3.3,4.4]

        ubyte ub
        byte bb
        uword uw
        word ww
        float fl


        fl = func(33)
        floats.print_f(fl)
        txt.chrout('\n')

        const ubyte i = 2
        const ubyte j = 3

        ub = ubarray[i]
        txt.print_ub(ub)
        txt.chrout('\n')

        bb = barray[i]
        txt.print_b(bb)
        txt.chrout('\n')

        uw = uwarray[i]
        txt.print_uw(uw)
        txt.chrout('\n')

        ww = warray[i]
        txt.print_w(ww)
        txt.chrout('\n')

        fl = farray[i]
        floats.print_f(fl)
        txt.chrout('\n')

        test_stack.test()
        txt.chrout('\n')
    }

    sub func(ubyte w) -> float {
        w = w*99
        return 3344.55
    }
}
