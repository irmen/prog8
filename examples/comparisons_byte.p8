%import c64utils

~ main {

    sub start()  {

        byte v1
        byte v2
        ubyte cr

        ; check stack usage:
        c64.STROUT("signed byte ")
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
        v1=-20
        v2=125
        c64.STROUT("v1=-20, v2=125\n")
        compare()

        v1=80
        v2=80
        c64.STROUT("v1 = v2 = 80\n")
        compare()

        v1=20
        v2=-111
        c64.STROUT("v1=20, v2=-111\n")
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
