%import c64utils
%import mathlib

~ main {

    sub start()  {

        word v1
        word v2
        ubyte cr

        ; done:
        ; ubyte all 6 comparisons
        ;  byte all 6 comparisons
        ; uword all 6 comparisons
        ;  word all 6 comparisons



        ; check stack usage:
        rsave()
        c64.STROUT("signed word ")
        rrestore()
        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT(' ')
        rrestore()

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

        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')
        rrestore()

        ; comparisons:
        rsave()
        v1=20
        v2=$00aa
        c64.STROUT("v1=20, v2=$00aa\n")
        rrestore()
        compare()

        rsave()
        v1=20
        v2=$7a00
        c64.STROUT("v1=20, v2=$7a00\n")
        rrestore()
        compare()

        rsave()
        v1=$7400
        v2=$22
        c64.STROUT("v1=$7400, v2=$22\n")
        rrestore()
        compare()

        rsave()
        v1=$7400
        v2=$2a00
        c64.STROUT("v1=$7400, v2=$2a00\n")
        rrestore()
        compare()

        rsave()
        v1=$7433
        v2=$2a00
        c64.STROUT("v1=$7433, v2=$2a00\n")
        rrestore()
        compare()

        rsave()
        v1=$7433
        v2=$2aff
        c64.STROUT("v1=$7433, v2=$2aff\n")
        rrestore()
        compare()

        ;  with negative numbers:
        rsave()
        v1=-512
        v2=$00aa
        c64.STROUT("v1=-512, v2=$00aa\n")
        rrestore()
        compare()

        rsave()
        v1=-512
        v2=$7a00
        c64.STROUT("v1=-512, v2=$7a00\n")
        rrestore()
        compare()

        rsave()
        v1=$7400
        v2=-512
        c64.STROUT("v1=$7400, v2=-512\n")
        rrestore()
        compare()

        rsave()
        v1=-20000
        v2=-1000
        c64.STROUT("v1=-20000, v2=-1000\n")
        rrestore()
        compare()

        rsave()
        v1=-1000
        v2=-20000
        c64.STROUT("v1=-1000, v2=-20000\n")
        rrestore()
        compare()

        rsave()
        v1=-1
        v2=32767
        c64.STROUT("v1=-1, v2=32767\n")
        rrestore()
        compare()

        rsave()
        v1=32767
        v2=-1
        c64.STROUT("v1=32767, v2=-1\n")
        rrestore()
        compare()

        rsave()
        v1=$7abb
        v2=$7abb
        c64.STROUT("v1 = v2 = 7abb\n")
        rrestore()
        compare()

        rsave()
        v1=$7a00
        v2=$7a00
        c64.STROUT("v1 = v2 = 7a00\n")
        rrestore()
        compare()

        rsave()
        v1=$aa
        v2=$aa
        c64.STROUT("v1 = v2 = aa\n")
        rrestore()
        compare()

    sub compare() {
        rsave()
        c64.STROUT("  ==  !=  <   >   <=  >=\n")
        rrestore()

        if v1==v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }
        if v1!=v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }

        if v1<v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }

        if v1>v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }

        if v1<=v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }


        if v1>=v2 {
            rsave()
            c64.STROUT("  Q ")
            rrestore()
        } else {
            rsave()
            c64.STROUT("  . ")
            rrestore()
        }
        c64.CHROUT('\n')


    }

    }

}
