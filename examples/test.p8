%import textio
%import math
%import string
%import floats
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {

;    sub ands(ubyte arg, ubyte b1, ubyte b2, ubyte b3, ubyte b4) -> ubyte {
;        return arg>b1 and arg>b2 and arg>b3 and arg>b4
;    }
;
;    sub ors(ubyte arg, ubyte b1, ubyte b2, ubyte b3, ubyte b4) -> ubyte {
;        return arg==b1 or arg==b2 or arg==b3 or arg==b4
;    }

;    sub mcCarthy() {
;        ubyte @shared a
;        ubyte @shared b
;
;        txt.print_ub(ands(10, 2,3,4,5))
;        txt.spc()
;        txt.print_ub(ands(10, 20,3,4,5))
;        txt.spc()
;        txt.print_ub(ors(10, 2,3,40,5))
;        txt.spc()
;        txt.print_ub(ors(10, 1,10,40,5))
;        txt.spc()
;    }

    sub start() {
        ; mcCarthy()


        ubyte @shared bb = %10110001
        byte @shared sb = -99
        bb *= 2
        bb /= 2
        bb *= 8
        bb /= 8
        txt.print_ub(bb)    ; 17
        txt.nl()

        sb *= 2
        sb /= 2
        sb *= 8
        sb /= 8
        txt.print_b(sb)     ; -3
        txt.nl()

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
