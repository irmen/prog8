%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

main {

    sub start() {

        ubyte[] uba = [10,0,2,8,5,4,3,9]
        uword[] uwa = [1000,0,200,8000,50,40000,3,900]
        byte[] ba = [-10,0,-2,8,5,4,-3,9]
        word[] wa = [-1000,0,-200,8000,50,31111,3,-900]
        float[] fla = [-2.2, 1.1, 3.3, 0.0]

        for ubyte ub in uba {
            c64scr.print_ub(ub)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uword uw in uwa {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for byte bb in ba {
            c64scr.print_b(bb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for word ww in wa {
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        sort(uba)
        sort(uwa)
        sort(ba)
        sort(wa)

        for ubyte ub2 in uba {
            c64scr.print_ub(ub2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uword uw2 in uwa {
            c64scr.print_uw(uw2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for byte bb2 in ba {
            c64scr.print_b(bb2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for word ww2 in wa {
            c64scr.print_w(ww2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        ubyte qq=X
        c64scr.print_ub(qq)

        ; TODO  2 for loops that both define the same loopvar -> double definition -> fix second for -> 'unknown symbol' ????

    }
}
