%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        for A in 0 to 4 {
            for Y in 0 to 3 {
                rsave()
                c64scr.print_ub(A)
                c64.CHROUT(',')
                rrestore()
                rsave()
                c64scr.print_ub(Y)
                c64.CHROUT('\n')
                rrestore()
            }
        }
    }

}
