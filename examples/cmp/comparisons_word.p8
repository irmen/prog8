%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        word v1
        word v2
        ubyte cr

        txt.print("signed word ")

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
        v2=$7a00
        txt.print("v1=20, v2=$7a00\n")
        compare()

        v1=$7400
        v2=$22
        txt.print("v1=$7400, v2=$22\n")
        compare()

        v1=$7400
        v2=$2a00
        txt.print("v1=$7400, v2=$2a00\n")
        compare()

        v1=$7433
        v2=$2a00
        txt.print("v1=$7433, v2=$2a00\n")
        compare()

        v1=$7433
        v2=$2aff
        txt.print("v1=$7433, v2=$2aff\n")
        compare()

        ;  with negative numbers:
        v1=-512
        v2=$00aa
        txt.print("v1=-512, v2=$00aa\n")
        compare()

        v1=-512
        v2=$7a00
        txt.print("v1=-512, v2=$7a00\n")
        compare()

        v1=$7400
        v2=-512
        txt.print("v1=$7400, v2=-512\n")
        compare()

        v1=-20000
        v2=-1000
        txt.print("v1=-20000, v2=-1000\n")
        compare()

        v1=-1000
        v2=-20000
        txt.print("v1=-1000, v2=-20000\n")
        compare()

        v1=-1
        v2=32767
        txt.print("v1=-1, v2=32767\n")
        compare()

        v1=32767
        v2=-1
        txt.print("v1=32767, v2=-1\n")
        compare()

        v1=$7abb
        v2=$7abb
        txt.print("v1 = v2 = 7abb\n")
        compare()

        v1=$7a00
        v2=$7a00
        txt.print("v1 = v2 = 7a00\n")
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
