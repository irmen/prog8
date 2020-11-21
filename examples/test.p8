%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        byte ub1
        byte ub2
        byte bb1
        byte bb2
        uword uw1
        uword uw2
        word ww1
        word ww2

        ub1 = 10
        ub2 = 11
        if ub1<ub2
            txt.chrout('.')
        else
            txt.chrout('!')
        if ub1<=ub2
            txt.chrout('.')
        else
            txt.chrout('!')
        if ub1>ub2
            txt.chrout('!')
        else
            txt.chrout('.')
        if ub1>=ub2
            txt.chrout('!')
        else
            txt.chrout('.')
        if ub1==ub2
            txt.chrout('!')
        else
            txt.chrout('.')
        if ub1!=ub2
            txt.chrout('.')
        else
            txt.chrout('!')

        test_stack.test()
        txt.chrout('\n')
    }
}
