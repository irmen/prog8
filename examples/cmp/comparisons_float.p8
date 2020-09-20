%import textio
%import floats
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        float v1
        float v2
        ubyte cr

        txt.print("floating point ")

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
        v2=666.66
        txt.print("v1=20, v2=666.66\n")
        compare()

        v1=-20
        v2=666.66
        txt.print("v1=-20, v2=666.66\n")
        compare()

        v1=666.66
        v2=555.55
        txt.print("v1=666.66, v2=555.55\n")
        compare()

        v1=3.1415
        v2=-3.1415
        txt.print("v1 = 3.1415, v2 = -3.1415\n")
        compare()

        v1=3.1415
        v2=3.1415
        txt.print("v1 = v2 = 3.1415\n")
        compare()

        v1=0
        v2=0
        txt.print("v1 = v2 = 0\n")
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
