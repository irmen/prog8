%import textio
%import floats
%import conv
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        float fl = 42.123
        float[] farray = [0.0, 0.0, 1.11, 2.22, 42.123, 0.0, -99.99]
        ubyte ix
        for ix in 0 to len(farray)-1 {
            floats.print_f(farray[ix])
            txt.spc()
        }
        txt.nl()
        reverse(farray)
        for ix in 0 to len(farray)-1 {
            floats.print_f(farray[ix])
            txt.spc()
        }
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
