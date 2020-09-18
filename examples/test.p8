%import c64lib
%import c64graphics
%import c64textio
;%import c64flt
;%option enable_floats
%zeropage basicsafe


main {

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict


    sub start()  {

        ubyte edgeIdx=0
        ubyte i

        ; TODO the following gives the correct output:
        for i in 2 downto 0 {
            ubyte e1
            ubyte e2
            e1 = edgeIdx
            edgeIdx ++
            e2 = edgeIdx
            edgeIdx ++
            txt.print_ub(e1)
            c64.CHROUT(',')
            txt.print_ub(e2)
            c64.CHROUT('\n')
        }

        c64.CHROUT('\n')
        edgeIdx=0
        ; TODO however with inline vardecl initializers the result is WRONG:
        for i in 2 downto 0 {
            ubyte e1 = edgeIdx
            edgeIdx++
            ubyte e2 = edgeIdx
            edgeIdx++
            txt.print_ub(e1)
            c64.CHROUT(',')
            txt.print_ub(e2)
            c64.CHROUT('\n')
        }
    }
}
