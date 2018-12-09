%import c64utils
%import mathlib

~ main {

    sub start()  {

        word v1
        word v2
        ubyte cr


        ; check stack usage:
        c64.STROUT("signed word ")
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
        v2=$7a00
        c64.STROUT("v1=20, v2=$7a00\n")
        compare()

        v1=$7400
        v2=$22
        c64.STROUT("v1=$7400, v2=$22\n")
        compare()

        v1=$7400
        v2=$2a00
        c64.STROUT("v1=$7400, v2=$2a00\n")
        compare()

        v1=$7433
        v2=$2a00
        c64.STROUT("v1=$7433, v2=$2a00\n")
        compare()

        v1=$7433
        v2=$2aff
        c64.STROUT("v1=$7433, v2=$2aff\n")
        compare()

        ;  with negative numbers:
        v1=-512
        v2=$00aa
        c64.STROUT("v1=-512, v2=$00aa\n")
        compare()

        v1=-512
        v2=$7a00
        c64.STROUT("v1=-512, v2=$7a00\n")
        compare()

        v1=$7400
        v2=-512
        c64.STROUT("v1=$7400, v2=-512\n")
        compare()

        v1=-20000
        v2=-1000
        c64.STROUT("v1=-20000, v2=-1000\n")
        compare()

        v1=-1000
        v2=-20000
        c64.STROUT("v1=-1000, v2=-20000\n")
        compare()

        v1=-1
        v2=32767
        c64.STROUT("v1=-1, v2=32767\n")
        compare()

        v1=32767
        v2=-1
        c64.STROUT("v1=32767, v2=-1\n")
        compare()

        v1=$7abb
        v2=$7abb
        c64.STROUT("v1 = v2 = 7abb\n")
        compare()

        v1=$7a00
        v2=$7a00
        c64.STROUT("v1 = v2 = 7a00\n")
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
