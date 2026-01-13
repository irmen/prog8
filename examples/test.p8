%import textio
%import floats
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        float @shared fv
        long @shared lv = -12345678

        fv=0
        cbm.SETTIML(0)
        repeat 1000 {
            fv = lv as float
        }
        txt.print_f(fv)
        txt.nl()
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
        txt.nl()

        fv=0
        cbm.SETTIML(0)
        repeat 1000 {
            floats.internal_long_to_float(&lv, &fv)
        }
        txt.print_f(fv)
        txt.nl()
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
        txt.nl()

        lv = 1024
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()

        lv = -1024
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()

        lv = 99999
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()

        lv =-99999
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()

        lv = 1122334455
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()

        lv = -1122334455
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()

        lv = 0
        txt.print_f(lv as float)
        txt.spc()
        floats.internal_long_to_float(&lv, &fv)
        txt.print_f(fv)
        txt.nl()
    }

}
