%import textio
%import floats
%import conv
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        float fl = -4.55
        floats.print_f(floats.fabs(fl))
        txt.nl()
        floats.print_f(floats.sin(fl))
        txt.nl()
        floats.print_f(floats.cos(fl))
        txt.nl()
        floats.print_f(floats.tan(fl))
        txt.nl()
        floats.print_f(floats.atan(fl))
        txt.nl()
        floats.print_f(floats.round(fl))
        txt.nl()
        floats.print_f(floats.floor(fl))
        txt.nl()
        floats.print_f(floats.ceil(fl))
        txt.nl()
        fl = 4.55
        floats.print_f(floats.ln(fl))
        txt.nl()
        floats.print_f(floats.log2(fl))
        txt.nl()
        floats.print_f(floats.sqrt(fl))
        txt.nl()
        floats.print_f(floats.pow(fl, 2.2))     ; TODO fix illegal quantity error
        txt.nl()
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
