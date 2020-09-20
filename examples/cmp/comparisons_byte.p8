%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        byte v1
        byte v2
        ubyte cr

        txt.print("signed byte ")

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

        ; comparisons:
        v1=-20
        v2=125
        txt.print("v1=-20, v2=125\n")
        compare()

        v1=80
        v2=80
        txt.print("v1 = v2 = 80\n")
        compare()

        v1=20
        v2=-111
        txt.print("v1=20, v2=-111\n")
        compare()

        return

        sub compare() {
        txt.print("  ==  !=  <   >   <=  >=\n")

        if v1==v2
            txt.print("  Q ")
        else
            txt.print("  . ")
        if v1!=v2
            txt.print("  Q ")
        else
            txt.print("  . ")

        if v1<v2
            txt.print("  Q ")
        else
            txt.print("  . ")

        if v1>v2
            txt.print("  Q ")
        else
            txt.print("  . ")

        if v1<=v2
            txt.print("  Q ")
        else
            txt.print("  . ")

        if v1>=v2
            txt.print("  Q ")
        else
            txt.print("  . ")
        c64.CHROUT('\n')

    }

    }
}
