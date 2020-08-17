%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        byte counterb
        word counterw

        for counterb in -10 to 11  {
            c64scr.print_b(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 11 to -10 step -1 {
            c64scr.print_b(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in -10 to 11 step 2 {
            c64scr.print_b(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 11 to -10 step -2 {
            c64scr.print_b(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in -10 to 11 step 3 {
            c64scr.print_b(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterb in 11 to -10 step -3 {
            c64scr.print_b(counterb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        for counterw in -10 to 11  {
            c64scr.print_w(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 11 to -10 step -1 {
            c64scr.print_w(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in -10 to 11 step 2 {
            c64scr.print_w(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 11 to -10 step -2 {
            c64scr.print_w(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in -10 to 11 step 3 {
            c64scr.print_w(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for counterw in 11 to -10 step -3 {
            c64scr.print_w(counterw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

    }
}
