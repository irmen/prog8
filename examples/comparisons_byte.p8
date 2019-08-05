%import c64utils
%zeropage basicsafe

main {

    sub start()  {

        byte v1
        byte v2
        ubyte cr

        c64scr.print("signed byte ")

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
        c64scr.print("v1=-20, v2=125\n")
        compare()

        v1=80
        v2=80
        c64scr.print("v1 = v2 = 80\n")
        compare()

        v1=20
        v2=-111
        c64scr.print("v1=20, v2=-111\n")
        compare()

        ubyte endX = X
        if endX == 255
            c64scr.print("\nstack x ok!\n")
        else
            c64scr.print("\nerror: stack x != 255 !\n")

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
