%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        float uw1
        const float uw2 = 2.2

        uw1 = 1.1
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
        uw1 = 2.2

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
