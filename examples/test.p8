%import textio
%import math
%import string
%import floats
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    ubyte value = 42

    sub inline_candidate() -> ubyte {
        return math.sin8u(value)
    }

    sub inline_candidate2() {
        value++
        return
    }

    sub add(ubyte first, ubyte second) -> ubyte {
        return first + second
    }

    sub mul(ubyte first, ubyte second) -> ubyte {
        return first * second
    }

    ubyte ix

    sub start() {

        ubyte @shared value1 = inline_candidate()
        txt.print_ub(value) ; 42
        txt.spc()
        inline_candidate2()
        inline_candidate2()
        inline_candidate2()
        txt.print_ub(value) ; 45
        txt.nl()
        txt.print_ub(inline_candidate())
        txt.nl()

        ubyte @shared value=99      ; TODO compiler warning about shadowing
        txt.print_ub(value)
        txt.nl()

        ubyte @shared add=99      ; TODO compiler warning about shadowing

;        ; a "pixelshader":
;        sys.gfx_enable(0)       ; enable lo res screen
;        ubyte shifter
;
;        repeat {
;            uword xx
;            uword yy = 0
;            repeat 240 {
;                xx = 0
;                repeat 320 {
;                    sys.gfx_plot(xx, yy, xx*yy + shifter as ubyte)
;                    xx++
;                }
;                yy++
;            }
;            shifter+=4
;        }
    }
}
