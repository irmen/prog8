%import textio
%import math
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
;    ubyte value = 42
;
;    sub inline_candidate() -> ubyte {
;        return math.sin8u(value)
;    }
;
;    sub add(ubyte first, ubyte second) -> ubyte {
;        return first + second
;    }
;
;    sub mul(ubyte first, ubyte second) -> ubyte {
;        return first * second
;    }

    sub ding(uword arg) {
        arg++
        txt.print_uw(arg)
    }

    sub start() {
        ding(0)
        txt.nl()
        ding(2)
        txt.nl()

;        ubyte value = inline_candidate()
;        byte svalue = 99
;        svalue = -svalue
;        @($5000) = not @($5000)
;        rol(value)
;        rol(@($5000))
;        ror(value)
;        ror(@($5000))
;        rol2(value)
;        rol2(@($5000))
;        ror2(value)
;        ror2(@($5000))
;        @($5000) <<= 1
;        @($5000) >>= 1
;        value <<= 1
;        value >>= 1
;        @($5000) <<= 3
;        @($5000) >>= 3
;        value <<= 3
;        value >>= 3
;        txt.print_ub(value)
;        txt.nl()
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
