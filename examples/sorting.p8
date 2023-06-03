%import textio
%zeropage basicsafe

; Note: this program can be compiled for multiple target systems.

main {

    sub start() {

        ubyte[] uba = [10,0,2,8,5,4,3,9]
        uword[] uwa = [1000,0,200,8000,50,40000,3,900]
        byte[] ba = [-10,0,-2,8,5,4,-3,9,-99]
        word[] wa = [-1000,0,-200,8000,50,31111,3,-900]

        txt.print("original\n")
        print_arrays()

        sort(uba)
        sort(uwa)
        sort(ba)
        sort(wa)

        txt.print("sorted\n")
        print_arrays()

        reverse(uba)
        reverse(uwa)
        reverse(ba)
        reverse(wa)

        txt.print("reversed\n")
        print_arrays()

        ;test_stack.test()
        return


        sub print_arrays() {
            ubyte ub
            uword uw
            byte bb
            word ww
            for ub in uba {
                txt.print_ub(ub)
                txt.chrout(',')
            }
            txt.nl()

            for uw in uwa {
                txt.print_uw(uw)
                txt.chrout(',')
            }
            txt.nl()

            for bb in ba {
                txt.print_b(bb)
                txt.chrout(',')
            }
            txt.nl()

            for ww in wa {
                txt.print_w(ww)
                txt.chrout(',')
            }
            txt.nl()
            txt.nl()
        }
    }
}
