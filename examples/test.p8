%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        c64scr.print_ub(5)
        c64.CHROUT('\n')
        return

        c64scr.print_ub(5)
        c64.CHROUT('\n')

        goto start

        c64scr.print_ub(5)
        c64.CHROUT('\n')

        exit(11)

        c64scr.print_ub(5)
        c64.CHROUT('\n')
    }
}
