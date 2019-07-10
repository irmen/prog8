%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        while true {
            c64scr.print_ub(c64.TIME_HI)
            c64.CHROUT(':')
            c64scr.print_ub(c64.TIME_MID)
            c64.CHROUT(':')
            c64scr.print_ub(c64.TIME_LO)
            c64.CHROUT('\n')
        }
    }

}
