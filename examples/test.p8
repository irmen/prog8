%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        A=0
        Y=0
        ubyte aa =0

        Y=10

        for A in 0 to 4 {
            for Y in 0 to 3 {
                rsave()
                c64scr.print_ub(A)
                c64.CHROUT(',')
                c64scr.print_ub(Y)
                c64.CHROUT('\n')
                rrestore()
            }
        }
    }

}
