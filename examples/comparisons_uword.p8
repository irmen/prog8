%import c64utils
%zeropage basicsafe

main {

    sub start()  {

        uword v1
        uword v2
        ubyte cr

        c64scr.print("unsigned word ")

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
        v2=$00aa
        c64scr.print("v1=20, v2=$00aa\n")
        compare()

        v1=20
        v2=$ea00
        c64scr.print("v1=20, v2=$ea00\n")
        compare()

        v1=$c400
        v2=$22
        c64scr.print("v1=$c400, v2=$22\n")
        compare()

        v1=$c400
        v2=$2a00
        c64scr.print("v1=$c400, v2=$2a00\n")
        compare()

        v1=$c433
        v2=$2a00
        c64scr.print("v1=$c433, v2=$2a00\n")
        compare()

        v1=$c433
        v2=$2aff
        c64scr.print("v1=$c433, v2=$2aff\n")
        compare()

        v1=$aabb
        v2=$aabb
        c64scr.print("v1 = v2 = aabb\n")
        compare()

        v1=$aa00
        v2=$aa00
        c64scr.print("v1 = v2 = aa00\n")
        compare()

        v1=$aa
        v2=$aa
        c64scr.print("v1 = v2 = aa\n")
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
