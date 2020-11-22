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
        float uw1
        float uw2
        word ww1
        word ww2

        uw1 = 1.1
        uw2 = 2.2
        if uw1<uw2
            txt.chrout('.')
        else
            txt.chrout('!')
        if uw1<=uw2
            txt.chrout('.')
        else
            txt.chrout('!')
        if uw1>uw2
            txt.chrout('!')
        else
            txt.chrout('.')

        if uw1>=uw2
            txt.chrout('!')
        else
            txt.chrout('.')
        if uw1==uw2
            txt.chrout('!')
        else
            txt.chrout('.')
        if uw1!=uw2
            txt.chrout('.')
        else
            txt.chrout('!')


        txt.chrout(' ')
        uw1 = -1.1
        uw2 = -1.1

        if uw1<uw2
            txt.chrout('!')
        else
            txt.chrout('.')
        if uw1<=uw2
            txt.chrout('.')
        else
            txt.chrout('!')
        if uw1>uw2
            txt.chrout('!')
        else
            txt.chrout('.')

        if uw1>=uw2
            txt.chrout('.')
        else
            txt.chrout('!')
        if uw1==uw2
            txt.chrout('.')
        else
            txt.chrout('!')
        if uw1!=uw2
            txt.chrout('!')
        else
            txt.chrout('.')

        test_stack.test()
        txt.chrout('\n')
    }
}
