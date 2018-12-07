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
        ub1=100
        ub2=199
        c64.STROUT("ub1=100,ub2=199\n")
        rrestore()

        if ub1==ub2 {
            rsave()
            c64.STROUT(" true: ub1==ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1==ub2\n")
            rrestore()
        }

        if ub1!=ub2 {
            rsave()
            c64.STROUT(" true: ub1!=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1!=ub2\n")
            rrestore()
        }


        rsave()
        ub1=199
        ub2=199
        c64.STROUT("ub1=ub2=199\n")
        rrestore()

        if ub1==ub2 {
            rsave()
            c64.STROUT(" true: ub1==ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1==ub2\n")
            rrestore()
        }

        if ub1!=ub2 {
            rsave()
            c64.STROUT(" true: ub1!=ub2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: ub1!=ub2\n")
            rrestore()
        }

        rsave()
        b1=50
        b2=111
        c64.STROUT("b1=50,b2=111\n")
        rrestore()

        if b1==b2 {
            rsave()
            c64.STROUT(" true: b1==b2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1==b2\n")
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


        rsave()
        b1=111
        b2=111
        c64.STROUT("b1=b2=111\n")
        rrestore()

        if b1==b2 {
            rsave()
            c64.STROUT(" true: b1==b2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: b1==b2\n")
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


        rsave()
        uw1=1234
        uw2=59999
        c64.STROUT("uw1=1234,uw2=59999\n")
        rrestore()

        if uw1==uw2 {
            rsave()
            c64.STROUT(" true: uw1==uw2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: uw1==uw2\n")
            rrestore()
        }

        if uw1!=uw2 {
            rsave()
            c64.STROUT(" true: uw1!=uw2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: uw1!=uw2\n")
            rrestore()
        }

        rsave()
        uw1=52999
        uw2=52999
        c64.STROUT("uw1=uw2=52999\n")
        rrestore()

        if uw1==uw2 {
            rsave()
            c64.STROUT(" true: uw1==uw2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: uw1==uw2\n")
            rrestore()
        }

        if uw1!=uw2 {
            rsave()
            c64.STROUT(" true: uw1!=uw2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: uw1!=uw2\n")
            rrestore()
        }

        rsave()
        w1=1234
        w2=-9999
        c64.STROUT("w1=1234, w2=-9999\n")
        rrestore()

        if w1==w2 {
            rsave()
            c64.STROUT(" true: w1==w2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: w1==w2\n")
            rrestore()
        }

        if w1!=w2 {
            rsave()
            c64.STROUT(" true: w1!=w2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: w1!=w2\n")
            rrestore()
        }

        rsave()
        w1=44
        w2=44
        c64.STROUT("w1=w2=44\n")
        rrestore()

        if w1==w2 {
            rsave()
            c64.STROUT(" true: w1==w2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: w1==w2\n")
            rrestore()
        }

        if w1!=w2 {
            rsave()
            c64.STROUT(" true: w1!=w2\n")
            rrestore()
        } else {
            rsave()
            c64.STROUT(" false: w1!=w2\n")
            rrestore()
        }
    }
}
