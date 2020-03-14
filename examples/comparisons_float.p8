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

        check_eval_stack()
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

    sub check_eval_stack() {
        c64scr.print("stack x=")
        c64scr.print_ub(X)
        if X==255
            c64scr.print(" ok\n")
        else
            c64scr.print(" error!\n")
    }
}
