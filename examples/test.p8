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

        for ubyte i in 0 to len(fla)-1 {
            c64flt.print_f(fla[i])
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

    }
}
