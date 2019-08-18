%import c64lib
%import c64utils
%zeropage basicsafe

main {

    sub start() {

        ubyte i

        for i in [1,3,5,99] {
            c64scr.print_ub(i)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in [1,3,5,99] {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

    }
}
