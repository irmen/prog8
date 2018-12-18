%import c64utils
%import mathlib

~ main {

    sub start()  {

        ubyte v1
        ubyte v2
        ubyte cr

        ; check stack usage:
        c64.STROUT("unsigned byte ")
        c64scr.print_ubyte(X)
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

        c64scr.print_ubyte(X)
        c64.CHROUT('\n')

        ; comparisons:
        v1=20
        v2=199
        c64.STROUT("v1=20, v2=199\n")
        compare()

        v1=80
        v2=80
        c64.STROUT("v1 = v2 = 80\n")
        compare()

        v1=220
        v2=10
        c64.STROUT("v1=220, v2=10\n")
        compare()

        c64scr.print_ubyte(X)
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
