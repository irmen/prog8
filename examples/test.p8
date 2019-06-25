%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        c64scr.print_ub(rnd())
        c64.CHROUT('\n')
        c64scr.print_ub(rnd())
        c64.CHROUT('\n')
        c64scr.print_ub(rnd())
        c64.CHROUT('\n')
        c64scr.print_uw(rndw())
        c64.CHROUT('\n')
        c64scr.print_uw(rndw())
        c64.CHROUT('\n')
        c64flt.print_f(rndf())
        c64.CHROUT('\n')
        c64flt.print_f(rndf())
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        A=rnd()
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        A=rnd()
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        A=rnd()
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        A=rnd()
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        A=rnd()
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        A=rnd()
        c64scr.print_ub(A)
        c64.CHROUT('\n')
    }
}
