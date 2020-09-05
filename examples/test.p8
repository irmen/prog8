%import c64textio
%import c64flt
%option enable_floats
%zeropage basicsafe
; TODO fix compilation when zeropage is not basicsafe on cx16


main {


    sub start() {

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict


    }

}
