%import c64textio
;%import c64flt
;%option enable_floats
%zeropage kernalsafe


main {


    sub start() {

        ubyte ub =9
        uword yy = 9999     ; this is okay (no 0-initialization generated) but... the next:
        uword xx = ub           ; TODO don't generate xx = 0 assignment if it's initialized with something else...

        ub++
        xx++
        yy++

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict

    }

}
