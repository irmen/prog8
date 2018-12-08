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

        ub2=ub1<ub2
        ub2=ub1<ub2
        ub2=ub1<ub2
        ub2=ub1<ub2
        ub2=ub1>ub2
        ub2=ub1>ub2
        ub2=ub1>ub2
        ub2=ub1>ub2
        ub2=ub1>ub2
        ub2=ub1<=ub2
        ub2=ub1<=ub2
        ub2=ub1<=ub2
        ub2=ub1<=ub2
        ub2=ub1>=ub2
        ub2=ub1>=ub2
        ub2=ub1>=ub2
        ub2=ub1>=ub2
        ub2=ub1>=ub2
        rsave()

        ub1=66
        ub2=199
        c64.STROUT("ub1=66,ub2=199\n")
        rrestore()

        if ub1<ub2 {
            rsave()
            c64.STROUT(" true: ub1<ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1<ub2\n")
            rrestore()
        }

        if ub1<=ub2 {
            rsave()
            c64.STROUT(" true: ub1<=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1<=ub2\n")
            rrestore()
        }

        if ub1>ub2 {
            rsave()
            c64.STROUT(" true: ub1>ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1>b2\n")
            rrestore()
        }

        if ub1>=ub2 {
            rsave()
            c64.STROUT(" true: ub1>=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1>=b2\n")
            rrestore()
        }


        rsave()
        ub1=199
        ub2=199
        c64.STROUT("ub1=ub2=199\n")
        rrestore()

        if ub1<ub2 {
            rsave()
            c64.STROUT(" true: ub1<ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1<ub2\n")
            rrestore()
        }

        if ub1<=ub2 {
            rsave()
            c64.STROUT(" true: ub1<=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1<=ub2\n")
            rrestore()
        }

        if ub1>ub2 {
            rsave()
            c64.STROUT(" true: ub1>ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1>ub2\n")
            rrestore()
        }

        if ub1>=ub2 {
            rsave()
            c64.STROUT(" true: ub1>=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1>=ub2\n")
            rrestore()
        }

        rsave()
        ub1=222
        ub2=88
        c64.STROUT("ub1=222,ub2=88\n")
        rrestore()

        if ub1<ub2 {
            rsave()
            c64.STROUT(" true: ub1<ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1<ub2\n")
            rrestore()
        }

        if ub1<=ub2 {
            rsave()
            c64.STROUT(" true: ub1<=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1<=ub2\n")
            rrestore()
        }

        if ub1>ub2 {
            rsave()
            c64.STROUT(" true: ub1>ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1>ub2\n")
            rrestore()
        }

        if ub1>=ub2 {
            rsave()
            c64.STROUT(" true: ub1>=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1>=ub2\n")
            rrestore()
        }


        rsave()
        c64scr.print_byte_decimal(X)
        c64.CHROUT('\n')
        rrestore()
    }
}
