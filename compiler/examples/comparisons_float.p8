%import c64utils
%import mathlib
%option enable_floats


~ main {

    sub start()  {

        float v1
        float v2
        ubyte cr

        ; done:
        ; ubyte all 6 comparisons
        ;  byte all 6 comparisons
        ; uword all 6 comparisons
        ;  word all 6 comparisons
        ; float all 6 comparisons


        ; check stack usage:
        rsave()
        c64.STROUT("floating point ")
        rrestore()
        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT(' ')
        rrestore()

        cr=v1==v2
        cr=v1==v2
        cr=v1==v2
        cr=v1!=v2
        cr=v1!=v2
        cr=v1!=v2
        cr=v1<v2
        cr=v1<v2
        cr=v1<v2
        cr=v1<v2
        cr=v1>v2
        cr=v1>v2
        cr=v1>v2
        cr=v1>v2
        cr=v1>v2
        cr=v1<=v2
        cr=v1<=v2
        cr=v1<=v2
        cr=v1<=v2
        cr=v1>=v2
        cr=v1>=v2
        cr=v1>=v2
        cr=v1>=v2
        cr=v1>=v2

        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')
        rrestore()

        ; comparisons:
        rsave()
        v1=20
        v2=666.66
        c64.STROUT("v1=20, v2=666.66\n")
        rrestore()
        compare()

        rsave()
        v1=-20
        v2=666.66
        c64.STROUT("v1=-20, v2=666.66\n")
        rrestore()
        compare()

        rsave()
        v1=666.66
        v2=555.55
        c64.STROUT("v1=666.66, v2=555.55\n")
        rrestore()
        compare()

        rsave()
        v1=3.1415
        v2=-3.1415
        c64.STROUT("v1 = 3.1415, v2 = -3.1415\n")
        rrestore()
        compare()

        rsave()
        v1=3.1415
        v2=3.1415
        c64.STROUT("v1 = v2 = 3.1415\n")
        rrestore()
        compare()

        rsave()
        v1=0
        v2=0
        c64.STROUT("v1 = v2 = 0\n")
        rrestore()
        compare()

    sub compare() {
        rsave()
        c64.STROUT("  ==  !=  <   >   <=  >=\n")
        rrestore()

        if v1==v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }
        if v1!=v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }

        if v1<v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }

        if v1>v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }

        if v1<=v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }


        if v1>=v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }
        c64.CHROUT('\n')


    }

    }

}
