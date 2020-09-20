%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        ubyte v1
        ubyte v2
        ubyte cr

        txt.print("unsigned byte ")

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
        v1=20
        v2=199
        txt.print("v1=20, v2=199\n")
        compare()

        v1=80
        v2=80
        txt.print("v1 = v2 = 80\n")
        compare()

        v1=220
        v2=10
        txt.print("v1=220, v2=10\n")
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
