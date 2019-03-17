%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    sub start() {
        for ubyte i in "hello\n" {
            c64.CHROUT(i)
        }

        for ubyte j in [1,2,3,4,5] {
            c64scr.print_ub(j)
            c64.CHROUT('\n')
        }
    }

}
