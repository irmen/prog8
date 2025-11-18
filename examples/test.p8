%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        manual()
        casts()
    }

    sub manual() {
        txt.print("\nwith conversion function:\n")
        long lv
        float f
        lv = 123456789
        txt.print_l(lv)
        txt.spc()
        floats.internal_cast_from_long(&lv, &f)
        txt.print_f(f)
        txt.spc()
        floats.internal_cast_as_long(&f, &lv)
        txt.print_l(lv)
        txt.nl()
        lv = -987654321
        txt.print_l(lv)
        txt.spc()
        floats.internal_cast_from_long(&lv, &f)
        txt.print_f(f)
        txt.spc()
        floats.internal_cast_as_long(&f, &lv)
        txt.print_l(lv)
        txt.nl()
        lv = -$111101
        txt.print_l(lv)
        txt.spc()
        txt.print_ulhex(lv, true)
        txt.spc()
        floats.internal_cast_from_long(&lv, &f)
        txt.print_f(f)
        txt.spc()
        floats.internal_cast_as_long(&f, &lv)
        txt.print_l(lv)
        txt.nl()
    }

    sub casts() {
        txt.print("\nwith casting:\n")
        long lv
        float f
        lv = 123456789
        txt.print_l(lv)
        txt.spc()
        f = lv as float
        txt.print_f(f)
        txt.spc()
        txt.print_l(f as long)
        txt.nl()
        lv = -987654321
        txt.print_l(lv)
        txt.spc()
        f = lv as float
        txt.print_f(f)
        txt.spc()
        txt.print_l(f as long)
        txt.nl()
        lv = -$111101
        txt.print_l(lv)
        txt.spc()
        txt.print_ulhex(lv, true)
        txt.spc()
        f = lv as float
        txt.print_f(f)
        txt.spc()
        txt.print_l(f as long)
        txt.nl()
    }


/*
    sub start2() {
        uword uw
        word sw
        long lv
        float fl


        uw = 44555
        fl = uw as float
        txt.print_f(fl)
        txt.nl()
        fl /= 2
        uw = fl as uword
        txt.print_uw(uw)
        txt.nl()

        sw = -8888
        fl = sw as float
        txt.print_f(fl)
        txt.nl()
        fl /= 2
        sw = fl as word
        txt.print_w(sw)
        txt.nl()

        lv = -99886666
        fl = lv as float
        txt.print_f(fl)
        txt.nl()
        fl /= 2
        lv = fl as long
        txt.print_l(lv)
        txt.nl()

    }
*/
}
