%import c64textio
;%import c64flt
;%option enable_floats
%zeropage kernalsafe


main {


    sub start() {

        ubyte b
        if b > 15  {
            b = 99
        } else {
            ; nothing
        }

        if b > 15  {
            ; nothing
        } else {
            b = 99
        }

        if b > 15  {
            ; nothing
        } else {
            ; nothing
        }

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict

    }

}
