%import textio
%import floats
%import test_stack
%zeropage dontuse


main {
    sub start() {
        uword xx
        float fl
        float fltotal=0.0
        ubyte ub = 22

        ub = 22
        fl = ub as float
        floats.print_f(fl)
        txt.nl()
        ub = 255
        fl = ub as float
        floats.print_f(fl)
        txt.nl()
        xx = 123
        fl = xx as float
        floats.print_f(fl)
        txt.nl()
        xx = 55555
        fl = xx as float
        floats.print_f(fl)
        txt.nl()

        fltotal=0.0
        for xx in 1 to 255 {
            fl = xx as float
            fltotal = fl * fl
        }

        test_stack.test()
    }
}
