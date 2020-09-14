%import c64textio
;%import c64flt
;%option enable_floats
%zeropage basicsafe
; TODO system reset should also work when kernal is paged out


main {


    sub start() {

        uword ub1
        word  ww1
        uword ii

        for ii in 0 to 20 {
            ; ub1 = ii
            ; ub1 *= 40       ; TODO implement non-stack optimized muls
            ; todo a = EXPRESSION * const -> is that optimized?
            ub1 = ii * 15
            txt.print_uw(ub1)
            c64.CHROUT(',')
            ub1 = 1+(ii * 15)
            txt.print_uw(ub1)
            c64.CHROUT('\n')
        }

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict

    }

}
