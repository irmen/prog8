%import test_stack
%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        word ww
        float y_f
        float fl
        for ww in -300 to 300 {         ; TODO fix crash if ww is not defined
            fl = ww as float
            floats.print_f(fl)
            txt.chrout(' ')
        }

        test_stack.test()

    }


}
