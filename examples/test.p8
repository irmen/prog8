%import textio
%import math
%import floats
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    ubyte value = 42

    sub inline_candidate() -> ubyte {
        return math.sin8u(value)
    }

    sub add(ubyte first, ubyte second) -> ubyte {
        return first + second
    }

    sub mul(ubyte first, ubyte second) -> ubyte {
        return first * second
    }

    sub start() {

        ubyte @shared value = inline_candidate()

        float fl1 = 1.1
        float fl2 = 2.2

        if fl1==fl2
            txt.print("equals!?\n")
        if fl1!=fl2
            txt.print("not equals.\n")
        fl1 = fl2
        if fl1==fl2
            txt.print("equals.\n")
        if fl1!=fl2
            txt.print("not equals!?\n")

        if fl1 <= fl2
            txt.print("yup\n")
        if fl1 > fl2
            txt.print("nope\n")
        fl1 = 3.3
        if fl1 <= fl2
            txt.print("yup\n")
        if fl1 > fl2
            txt.print("nope\n")

;        txt.print_ub(inline_candidate())
;        txt.nl()

;        ubyte value = add(3,4) |> add(10) |> mul(2) |> math.sin8u()
;        txt.print_ub(value)
;        txt.nl()
;        uword wvalue = add(3,4) |> add($30) |> mkword($ea)
;        txt.print_uwhex(wvalue, true)
;        txt.nl()

        ; expected output: aaabbb aaa bbb

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
