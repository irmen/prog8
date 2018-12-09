%import c64utils
%import mathlib
%option enable_floats

~ main {

    sub start()  {

        ubyte ub1
        ubyte ub2
        byte b1
        byte b2
        uword uw1
        uword uw2
        word w1
        word w2
        float f1
        float f2


        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')
        rrestore()

        ub2=b1<b2
        ub2=b1<b2
        ub2=b1<b2
        ub2=b1<b2
        ub2=b1>b2
        ub2=b1>b2
        ub2=b1>b2
        ub2=b1>b2
        ub2=b1>b2
        ub2=b1<=b2
        ub2=b1<=b2
        ub2=b1<=b2
        ub2=b1<=b2
        ub2=b1>=b2
        ub2=b1>=b2
        ub2=b1>=b2
        ub2=b1>=b2
        ub2=b1>=b2

        rsave()
        b1=-20
        b2=99
        c64.STROUT("b1=-20,b2=99\n")
        rrestore()
        compare()

    sub compare() {
        if b1<b2 {
            rsave()
            c64.STROUT("  true: b1<b2  ")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1<b2  ")
            rrestore()
        }

        if b1>b2 {
            rsave()
            c64.STROUT("  true: b1>b2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1>b2\n")
            rrestore()
        }

        if b1<=b2 {
            rsave()
            c64.STROUT("  true: b1<=b2 ")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1<=b2 ")
            rrestore()
        }


        if b1>=b2 {
            rsave()
            c64.STROUT("  true: b1>=b2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1>=b2\n")
            rrestore()
        }

        if b1==b2 {
            rsave()
            c64.STROUT("  true: b1==b2  ")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1==b2  ")
            rrestore()
        }
        if b1!=b2 {
            rsave()
            c64.STROUT(" true: b1!=b2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1!=b2\n")
            rrestore()
        }

    }


        rsave()
        b1=-80
        b2=-80
        c64.STROUT("b1=b2=-80\n")
        rrestore()

        compare()

        rsave()
        b1=120
        b2=-10
        c64.STROUT("b1=120,b2=-10\n")
        rrestore()

        compare()

        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')
        rrestore()
    }
}
