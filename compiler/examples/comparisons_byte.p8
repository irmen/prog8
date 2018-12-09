%import c64utils
%import mathlib

~ main {

    sub start()  {

        byte v1
        byte v2
        ubyte cr

        ; done:
        ; ubyte all 6 comparisons
        ;  byte all 6 comparisons



        ; check stack usage:
        rsave()
        c64.STROUT("signed byte ")
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
        v1=-20
        v2=125
        c64.STROUT("v1=-20, v2=125\n")
        rrestore()
        compare()

        rsave()
        v1=80
        v2=80
        c64.STROUT("v1 = v2 = 80\n")
        rrestore()

        compare()

        rsave()
        v1=20
        v2=-111
        c64.STROUT("v1=20, v2=-111\n")
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
