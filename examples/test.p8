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

;        for A in [1,3,5,99] {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in 10 to 20 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')

;        for var2 in 10 to 20 {
;            c64scr.print_ub(var2)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for ubyte var3 in 10 to 20 {
;            c64scr.print_ub(var3)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for bvar in -5 to 5 {
;            c64scr.print_b(bvar)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')

;        for float fl in [1.1, 2.2, 5.5, 99.99] {
;            c64flt.print_f(fl)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
    }
}
