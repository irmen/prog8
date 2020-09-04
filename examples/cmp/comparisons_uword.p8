%import c64textio
%zeropage basicsafe

main {

    sub start()  {

        uword v1
        uword v2
        ubyte cr

        txt.print("unsigned word ")

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
        txt.print("v1=20, v2=$00aa\n")
        compare()

        v1=20
        v2=$ea00
        txt.print("v1=20, v2=$ea00\n")
        compare()

        v1=$c400
        v2=$22
        txt.print("v1=$c400, v2=$22\n")
        compare()

        v1=$c400
        v2=$2a00
        txt.print("v1=$c400, v2=$2a00\n")
        compare()

        v1=$c433
        v2=$2a00
        txt.print("v1=$c433, v2=$2a00\n")
        compare()

        v1=$c433
        v2=$2aff
        txt.print("v1=$c433, v2=$2aff\n")
        compare()

        v1=$aabb
        v2=$aabb
        txt.print("v1 = v2 = aabb\n")
        compare()

        v1=$aa00
        v2=$aa00
        txt.print("v1 = v2 = aa00\n")
        compare()

        v1=$aa
        v2=$aa
        txt.print("v1 = v2 = aa\n")
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
