%import c64utils
%import mathlib
%option enable_floats


~ main {

    sub start()  {

        float v1
        float v2
        ubyte cr

        ; check stack usage:
        c64.STROUT("floating point ")
        c64scr.print_ub(X)
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

        c64scr.print_ub(X)
        c64.CHROUT('\n')

        ; comparisons:
        v1=20
        v2=666.66
        c64.STROUT("v1=20, v2=666.66\n")
        compare()

        v1=-20
        v2=666.66
        c64.STROUT("v1=-20, v2=666.66\n")
        compare()

        v1=666.66
        v2=555.55
        c64.STROUT("v1=666.66, v2=555.55\n")
        compare()

        v1=3.1415
        v2=-3.1415
        c64.STROUT("v1 = 3.1415, v2 = -3.1415\n")
        compare()

        v1=3.1415
        v2=3.1415
        c64.STROUT("v1 = v2 = 3.1415\n")
        compare()

        v1=0
        v2=0
        c64.STROUT("v1 = v2 = 0\n")
        compare()

        c64scr.print_ub(X)
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
