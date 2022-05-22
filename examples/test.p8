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

        ubyte bb
        for bb in 250 to 255 {
            txt.print_ub(bb)
            txt.spc()
        }
        txt.nl()

;        ; a "pixelshader":
        sys.gfx_enable(0)       ; enable lo res screen
        ubyte shifter

        repeat {
            uword xx
            uword yy = 0
            repeat 240 {
                xx = 0
                repeat 320 {
                    sys.gfx_plot(xx, yy, xx*yy + shifter as ubyte)
                    xx++
                }
                yy++
            }
            shifter+=4
        }
    }
}
