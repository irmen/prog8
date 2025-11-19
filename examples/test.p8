%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv = -1
        word @shared wv = -1
        byte @shared bv = -1
        float @shared fv = -1.1
        bool b1, b2, b3, b4 = false

        b1 = bv<0
        b2 = wv<0
        b3 = lv<0
        b4 = fv<0
        txt.print_bool(b1)
        txt.print_bool(b2)
        txt.print_bool(b3)
        txt.print_bool(b4)
        txt.nl()

        b1=b2=b3=b4=false
        b1 = sgn(bv)<0
        b2 = sgn(wv)<0
        b3 = sgn(lv)<0
        b4 = sgn(fv)<0
        txt.print_bool(b1)
        txt.print_bool(b2)
        txt.print_bool(b3)
        txt.print_bool(b4)
        txt.nl()

        b1 = sgn(bv)>0
        b2 = sgn(wv)>0
        b3 = sgn(lv)>0
        b4 = sgn(fv)>0
        txt.print_bool(b1)
        txt.print_bool(b2)
        txt.print_bool(b3)
        txt.print_bool(b4)
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
