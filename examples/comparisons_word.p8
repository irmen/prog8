%import c64utils
%zeropage basicsafe

main {

    sub start()  {

        word v1
        word v2
        ubyte cr

        c64scr.print("signed word ")

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
        v2=$7a00
        c64scr.print("v1=20, v2=$7a00\n")
        compare()

        v1=$7400
        v2=$22
        c64scr.print("v1=$7400, v2=$22\n")
        compare()

        v1=$7400
        v2=$2a00
        c64scr.print("v1=$7400, v2=$2a00\n")
        compare()

        v1=$7433
        v2=$2a00
        c64scr.print("v1=$7433, v2=$2a00\n")
        compare()

        v1=$7433
        v2=$2aff
        c64scr.print("v1=$7433, v2=$2aff\n")
        compare()

        ;  with negative numbers:
        v1=-512
        v2=$00aa
        c64scr.print("v1=-512, v2=$00aa\n")
        compare()

        v1=-512
        v2=$7a00
        c64scr.print("v1=-512, v2=$7a00\n")
        compare()

        v1=$7400
        v2=-512
        c64scr.print("v1=$7400, v2=-512\n")
        compare()

        v1=-20000
        v2=-1000
        c64scr.print("v1=-20000, v2=-1000\n")
        compare()

        v1=-1000
        v2=-20000
        c64scr.print("v1=-1000, v2=-20000\n")
        compare()

        v1=-1
        v2=32767
        c64scr.print("v1=-1, v2=32767\n")
        compare()

        v1=32767
        v2=-1
        c64scr.print("v1=32767, v2=-1\n")
        compare()

        v1=$7abb
        v2=$7abb
        c64scr.print("v1 = v2 = 7abb\n")
        compare()

        v1=$7a00
        v2=$7a00
        c64scr.print("v1 = v2 = 7a00\n")
        compare()

        v1=$aa
        v2=$aa
        c64scr.print("v1 = v2 = aa\n")
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
        if X!=255 {
            c64scr.print("x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }

}
