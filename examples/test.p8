%import c64textio
;%import c64flt
;%option enable_floats
%zeropage kernalsafe


main {


    sub start() {

        %asm {{
            sei
            ldy  #0
            sty  $1
            lda  #0
-           sta  $f000,y
            iny
            bne  -
-           lda  $f000,y
            sta  $0400,y
            iny
            bne  -
        }}

        repeat 60000 {
            ubyte a = sin (3)
            a++
        }

;asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  { ...}
; TODO dont cause name conflict if we define sub or sub with param 'color' or even a var 'color' later.

;   sub color(...) {}
;   sub other(ubyte color) {}    ; TODO don't cause name conflict

    }

}
