%zeropage basicsafe

main {

    sub start() {
        byte bvar
        ubyte var2

        ubyte[] barr = [22,33,44,55,66]
        word[] warr = [-111,222,-333,444]

        for A in "hello" {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in [1,3,5,99] {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in 10 to 20 {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in 20 to 10 step -1 {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in 10 to 21 step 3 {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in 24 to 10 step -3 {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for A in barr {
            c64scr.print_ub(A)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        for ubyte cc in "hello" {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ubyte cc2 in [1,3,5,99] {
            c64scr.print_ub(cc2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ubyte cc3 in 10 to 20 {
            c64scr.print_ub(cc3)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ubyte cc4 in 20 to 10 step -1 {
            c64scr.print_ub(cc4)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ubyte cc5 in 10 to 21 step 3 {
            c64scr.print_ub(cc5)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ubyte cc6 in 24 to 10 step -3 {
            c64scr.print_ub(cc6)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ubyte cc7 in barr {
            c64scr.print_ub(cc7)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        for uword ww1 in [1111, 2222, 3333] {
            c64scr.print_uw(ww1)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for word ww2 in warr {
            c64scr.print_w(ww2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uword ww3 in 1111 to 1117  {
            c64scr.print_uw(ww3)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uword ww3b in 2000 to 1995 step -1 {
            c64scr.print_uw(ww3b)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uword ww3c in 1111 to 50000 step 4444 {
            c64scr.print_uw(ww3c)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for word ww4 in 999 to -999 step -500 {
            c64scr.print_w(ww4)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }
}
