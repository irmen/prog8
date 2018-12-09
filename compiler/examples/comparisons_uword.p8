%import c64utils
%import mathlib

~ main {

    sub start()  {

        uword v1
        uword v2
        ubyte cr

        ; check stack usage:
        c64.STROUT("unsigned word ")
        c64scr.print_byte_decimal(X)
        c64.CHROUT(' ')

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

        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')

        ; comparisons:
        v1=20
        v2=$00aa
        c64.STROUT("v1=20, v2=$00aa\n")
        compare()

        v1=20
        v2=$ea00
        c64.STROUT("v1=20, v2=$ea00\n")
        compare()

        v1=$c400
        v2=$22
        c64.STROUT("v1=$c400, v2=$22\n")
        compare()

        v1=$c400
        v2=$2a00
        c64.STROUT("v1=$c400, v2=$2a00\n")
        compare()

        v1=$c433
        v2=$2a00
        c64.STROUT("v1=$c433, v2=$2a00\n")
        compare()

        v1=$c433
        v2=$2aff
        c64.STROUT("v1=$c433, v2=$2aff\n")
        compare()

        v1=$aabb
        v2=$aabb
        c64.STROUT("v1 = v2 = aabb\n")
        compare()

        v1=$aa00
        v2=$aa00
        c64.STROUT("v1 = v2 = aa00\n")
        compare()

        v1=$aa
        v2=$aa
        c64.STROUT("v1 = v2 = aa\n")
        compare()

        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')

        return

    sub compare() {
        c64.STROUT("  ==  !=  <   >   <=  >=\n")

        if v1==v2
            c64.STROUT("  Q ")
        else
            c64.STROUT("  . ")
        if v1!=v2
            c64.STROUT("  Q ")
        else
            c64.STROUT("  . ")

        if v1<v2
            c64.STROUT("  Q ")
        else
            c64.STROUT("  . ")

        if v1>v2
            c64.STROUT("  Q ")
        else
            c64.STROUT("  . ")

        if v1<=v2
            c64.STROUT("  Q ")
        else
            c64.STROUT("  . ")

        if v1>=v2
            c64.STROUT("  Q ")
        else
            c64.STROUT("  . ")
        c64.CHROUT('\n')

    }

    }

}
