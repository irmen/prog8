%import c64lib
%import c64utils
%zeropage basicsafe

main {

    sub start() {

        ubyte[] uba = [10,0,2,8,5,4,3,9]
        uword[] uwa = [1000,0,200,8000,50,40000,3,900]
        byte[] ba = [-10,0,-2,8,5,4,-3,9,-99]
        word[] wa = [-1000,0,-200,8000,50,31111,3,-900]

        c64scr.print("original\n")
        print_arrays()

        sort(uba)
        sort(uwa)
        sort(ba)
        sort(wa)

        c64scr.print("sorted\n")
        print_arrays()

        reverse(uba)
        reverse(uwa)
        reverse(ba)
        reverse(wa)

        c64scr.print("reversed\n")
        print_arrays()

        return


        sub print_arrays() {
            ubyte ub
            uword uw
            byte bb
            word ww
            for ub in uba {
                c64scr.print_ub(ub)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')

            for uw in uwa {
                c64scr.print_uw(uw)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')

            for bb in ba {
                c64scr.print_b(bb)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')

            for ww in wa {
                c64scr.print_w(ww)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')
            c64.CHROUT('\n')
        }
    }
}
