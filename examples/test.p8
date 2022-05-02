%import textio
%import floats
%import conv
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        float fl1 = 500.0
        float fzero = 0.0
        floats.print_f(fl1 / fzero)
        txt.nl()

        ubyte ub1 = 50
        ubyte ubzero = 0
        txt.print_ub(ub1/ubzero)
        txt.nl()

        uword uw1 = 5000
        uword uwzero = 0
        txt.print_uw(uw1/uwzero)
        txt.nl()

;        float fl = 500.0
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
