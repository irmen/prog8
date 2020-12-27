%import test_stack
%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        word ww
        float x_f = -300.0
        float y_f
        for ww in -300 to 300 {         ; TODO fix crash if ww is not defined
            ;fl = ww as float         ; TODO doesn't work???
            y_f = cos(x_f/30.0)*60 - x_f/1.7
            ; gfx2.plot(ww + 320 as uword, (y_f + 240) as uword, 1)
            x_f += 1.0
        }

    }


}
