%zeropage basicsafe

; TODO implement asm generation for all loops here

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

        for A in [1,3,5,99] {           ; TODO FIX COMPILER ERROR array should have been moved to the heap
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

        ubyte cc
        for cc in "hello" {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for cc in [1,3,5,99] {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for cc in 10 to 20 {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for cc in 20 to 10 step -1 {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for cc in 10 to 21 step 3 {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for cc in 24 to 10 step -3 {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for cc in barr {
            c64scr.print_ub(cc)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        uword uw
        for uw in [1111, 2222, 3333] {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uw in warr {
            c64scr.print_w(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uw in 1111 to 1117  {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uw in 2000 to 1995 step -1 {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uw in 1111 to 50000 step 4444 {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        word ww
        for ww in 999 to -999 step -500 {
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }
}
