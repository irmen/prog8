%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        float @shared fv

        fv = -22
        compares()
        fv=0
        compares()
        fv=33
        compares()
        txt.nl()

        fv = -22
        signs()
        fv=0
        signs()
        fv=33
        signs()

        sub compares() {
            txt.print("compares\n")
            if fv==0
                txt.print(" ==0\n")
            if fv<=0
                txt.print(" <=0\n")
            if fv<0
                txt.print(" <0\n")
            if fv>=0
                txt.print(" >=0\n")
            if fv>0
                txt.print(" >0\n")
        }

        sub signs() {
            txt.print("signs\n")
            if sgn(fv)==0
                txt.print(" ==0\n")
            if sgn(fv)<=0
                txt.print(" <=0\n")
            if sgn(fv)<0
                txt.print(" <0\n")
            if sgn(fv)>=0
                txt.print(" >=0\n")
            if sgn(fv)>0
                txt.print(" >0\n")
        }
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
