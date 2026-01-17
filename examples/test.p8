%import textio
%import floats
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        float @shared fv = 1.2345
        long @shared lv = -998877

        txt.print_f(lv as float * 2.0)
        txt.nl()
        txt.print_f(lv as float * 2.0)
        txt.nl()
        txt.print_f(lv as float * 2.0)
        txt.nl()
        txt.print_f(lv as float * 2.0)
        txt.nl()
        fv = lv as float
        txt.print_f(fv)
        txt.nl()
        fv = lv as float
        txt.print_f(fv)
        txt.nl()
        fv = lv as float
        txt.print_f(fv)
        txt.nl()
        fv = lv as float
        txt.print_f(fv)
        txt.nl()
    }

}
