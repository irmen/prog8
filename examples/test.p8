%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        ; TODO uword var = rndw() % 640 doesn't work???   works if its in an expression.

        x_f = -300.0
        for ww in -300 to 300 {
            ;fl = ww as float / 10.0         ; TODO doesn't work???
            y_f = cos(x_f/30)*60 - x_f/1.7
            gfx2.plot(ww + 320 as uword, (y_f + 240) as uword, 1)
            x_f += 1.0
        }

    }


}
