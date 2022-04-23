%import textio
; %import floats
%import conv
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub newstring() -> str {
        return "new"
    }

    sub start() {
        str name = "irmen\n"
        txt.print(name)
        name = "pipo\n"
        txt.print(name)

        ubyte cc
        ubyte[] array = [11,22,33,44]
        for cc in array {
            txt.print_ub(cc)
            txt.spc()
        }
        txt.nl()
        array = [99,88,77,66]
        for cc in array {
            txt.print_ub(cc)
            txt.spc()
        }
        txt.nl()

        txt.print_ub0(99)
        txt.spc()
        txt.print_ub(99)
        txt.nl()
        txt.print_uw0(9988)
        txt.spc()
        txt.print_uw(9988)
        txt.nl()

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

;        ; a "pixelshader":
;        void syscall1(8, 0)      ; enable lo res creen
;        ubyte shifter
;
;        ; pokemon(1,0)
;
;        repeat {
;            uword xx
;            uword yy = 0
;            repeat 240 {
;                xx = 0
;                repeat 320 {
;                    syscall3(10, xx, yy, xx*yy + shifter)   ; plot pixel
;                    xx++
;                }
;                yy++
;            }
;            shifter+=4
;        }
    }
}
