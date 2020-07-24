%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start()  {

        float v1
        float v2
        ubyte cr

        c64scr.print("floating point ")

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
        c64scr.print("v1=20, v2=666.66\n")
        compare()

        v1=-20
        v2=666.66
        c64scr.print("v1=-20, v2=666.66\n")
        compare()

        v1=666.66
        v2=555.55
        c64scr.print("v1=666.66, v2=555.55\n")
        compare()

        v1=3.1415
        v2=-3.1415
        c64scr.print("v1 = 3.1415, v2 = -3.1415\n")
        compare()

        v1=3.1415
        v2=3.1415
        c64scr.print("v1 = v2 = 3.1415\n")
        compare()

        v1=0
        v2=0
        c64scr.print("v1 = v2 = 0\n")
        compare()

        return

        sub compare() {
        c64scr.print("  ==  !=  <   >   <=  >=\n")

        if v1==v2
            c64scr.print("  Q ")
        else
            c64scr.print("  . ")
        if v1!=v2
            c64scr.print("  Q ")
        else
            c64scr.print("  . ")

        if v1<v2
            c64scr.print("  Q ")
        else
            c64scr.print("  . ")

        if v1>v2
            c64scr.print("  Q ")
        else
            c64scr.print("  . ")

        if v1<=v2
            c64scr.print("  Q ")
        else
            c64scr.print("  . ")

        if v1>=v2
            c64scr.print("  Q ")
        else
            c64scr.print("  . ")
        c64.CHROUT('\n')

    }

    }

}
