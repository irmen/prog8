%import textio
%import floats
%import conv
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        float fl = -4.567
        float fl2 = fl / 1.0
        floats.print_f(fl2)
        txt.nl()
        txt.print_ub(fl2 as ubyte)    ; TODO fix crash source dt size must be less or equal to target dt size
        txt.nl()
        txt.print_b(fl2 as byte)      ; TODO fix crash
        txt.nl()
        txt.print_uw(fl2 as uword)    ; TODO fix crash
        txt.nl()
        txt.print_w(fl2 as word)      ; TODO fix crash
        txt.nl()

;        txt.print("rad 180 = ")
;        floats.print_f(floats.rad(180.0))
;        txt.print("rad 360 = ")
;        floats.print_f(floats.rad(360.0))
;        txt.print("deg 2 = ")
;        floats.print_f(floats.deg(2.0))
;        txt.print("deg pi = ")
;        floats.print_f(floats.deg(floats.PI))
        sys.exit(42)
;        floats.print_f(-42.42)
;        float f1 = 1.2345
;        float f2 = -9.99
;        float f3
;        f3 = floats.sin(f3)
;        floats.print_f(f3)
;        txt.nl()

;    float f1 = 1.555
;    floats.print_f(floats.sin(f1))
;    txt.nl()
;    floats.print_f(floats.cos(f1))
;    txt.nl()
;    floats.print_f(floats.tan(f1))
;    txt.nl()
;    floats.print_f(floats.atan(f1))
;    txt.nl()
;    floats.print_f(floats.ln(f1))
;    txt.nl()
;    floats.print_f(floats.log2(f1))
;    txt.nl()
;    floats.print_f(floats.sqrt(f1))
;    txt.nl()
;    floats.print_f(floats.rad(f1))
;    txt.nl()
;    floats.print_f(floats.deg(f1))
;    txt.nl()
;    floats.print_f(floats.round(f1))
;    txt.nl()
;    floats.print_f(floats.floor(f1))
;    txt.nl()
;    floats.print_f(floats.ceil(f1))
;    txt.nl()
;    floats.print_f(floats.rndf())
;    txt.nl()
;            "sin", "cos", "tan", "atan",
;            "ln", "log2", "sqrt", "rad",
;            "deg", "round", "floor", "ceil", "rndf"

        ; a "pixelshader":
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
