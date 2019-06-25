%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        ubyte i=99

        for ubyte j in 20 to 40 step 5 {
            c64scr.print_ub(j)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for uword j in 200 to 400 step 5 {
            c64scr.print_uw(j)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for i in 200 to 50 step -20 {
            c64scr.print_ub(i)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for i in "hello" {
            c64scr.print_ub(i)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for i in [1,2,3,4,5] {
            c64scr.print_ub(i)
            c64.CHROUT(',')
        }

        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }
}
