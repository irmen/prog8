%import cx16textio
%zeropage basicsafe

main {
    sub start() {

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict

        ; TODO fix var storage in ASM when declared const:
        float  PI        = 3.141592653589793
        float  TWOPI	 = 6.283185307179586
        float  ZERO      = 0.0
        float  ONE       = 1.0


        float @zp rz                ; TODO compiler warning that float can't be in ZP?


    }

}
