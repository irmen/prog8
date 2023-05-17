%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        word ww = -1234
        uword uww = 1234
        float fl = 123.34
        byte bb = -123
        ubyte ub = 123

        txt.print_w(clamp(ww, -2000, -500))
        txt.spc()
        txt.print_w(clamp(ww, -1000, -500))
        txt.spc()
        txt.print_w(clamp(ww, -2000, -1500))
        txt.nl()
        txt.print_uw(clamp(uww, 500, 2000))
        txt.spc()
        txt.print_uw(clamp(uww, 500, 1000))
        txt.spc()
        txt.print_uw(clamp(uww, 1500, 2000))
        txt.nl()

        txt.print_b(clamp(bb, -127, -50))
        txt.spc()
        txt.print_b(clamp(bb, -100, -50))
        txt.spc()
        txt.print_b(clamp(bb, -127, -125))
        txt.nl()
        txt.print_ub(clamp(ub, 50, 200))
        txt.spc()
        txt.print_ub(clamp(ub, 50, 100))
        txt.spc()
        txt.print_ub(clamp(ub, 150, 200))
        txt.nl()

        floats.print_f(floats.clampf(fl, 50.0, 200.0))
        txt.spc()
        floats.print_f(floats.clampf(fl, 50.0, 100.0))
        txt.spc()
        floats.print_f(floats.clampf(fl, 150.0, 200.0))
        txt.nl()
    }
}

