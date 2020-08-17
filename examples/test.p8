%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        ubyte counterb
        uword counterw


        for counterb in 0 to 10 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 10 to 30 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 250 to 255 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 10 to 0 step -1 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 10 to 1 step -1 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 30 to 10 step -1 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 255 to 250 step -1 {
            c64scr.print_ub(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        for counterw in 0 to 10 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 10 to 30 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 250 to 255 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 10 to 0 step -1 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 10 to 1 step -1 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 30 to 10 step -1 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 255 to 250 step -1 {
            c64scr.print_uw(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
    }
}
