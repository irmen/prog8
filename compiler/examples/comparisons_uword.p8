%import c64utils
%import mathlib

~ main {

    sub start()  {

        uword v1
        uword v2
        ubyte cr

        ; done:
        ; ubyte all 6 comparisons
        ;  byte all 6 comparisons
        ; uword all 6 comparisons



        ; check stack usage:
        rsave()
        c64.STROUT("unsigned word ")
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
        v2=$00aa
        c64.STROUT("v1=20, v2=$00aa\n")
        rrestore()
        compare()

        rsave()
        v1=20
        v2=$ea00
        c64.STROUT("v1=20, v2=$ea00\n")
        rrestore()
        compare()

        rsave()
        v1=$c400
        v2=$22
        c64.STROUT("v1=$c400, v2=$22\n")
        rrestore()
        compare()

        rsave()
        v1=$c400
        v2=$2a00
        c64.STROUT("v1=$c400, v2=$2a00\n")
        rrestore()
        compare()

        rsave()
        v1=$c433
        v2=$2a00
        c64.STROUT("v1=$c433, v2=$2a00\n")
        rrestore()
        compare()

        rsave()
        v1=$c433
        v2=$2aff
        c64.STROUT("v1=$c433, v2=$2aff\n")
        rrestore()
        compare()

        rsave()
        v1=$aabb
        v2=$aabb
        c64.STROUT("v1 = v2 = aabb\n")
        rrestore()
        compare()

        rsave()
        v1=$aa00
        v2=$aa00
        c64.STROUT("v1 = v2 = aa00\n")
        rrestore()
        compare()

        rsave()
        v1=$aa
        v2=$aa
        c64.STROUT("v1 = v2 = aa\n")
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
