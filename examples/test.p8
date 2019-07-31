%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    sub start() {
        byte bvar
        ubyte var2

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
        c64.CHROUT('\n')


;        for float fl in [1.1, 2.2, 5.5, 99.99] {
;            c64flt.print_f(fl)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
    }
}
