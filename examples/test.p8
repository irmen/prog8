%import textio
%import math
%import string
%import floats
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

other {
    ubyte value = 42

    sub getter() -> ubyte {
        return value
    }
}

main {


    sub start() {
        ubyte @shared ix = other.getter()
        ix = other.getter()
        ix++
        ix = other.getter()
        ix++
        ix = other.getter()
        ix++
        ix = other.getter()
        ix++
        ix = other.getter()
        ix++

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
