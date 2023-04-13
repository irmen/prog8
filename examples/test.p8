%import textio
%import math
%import floats
%import string

main {
    ubyte a
    sub func1() {
        a++
    }

    sub func2() -> ubyte {
        a++
        return a
    }

    sub start() {
        a=42
        func1()
        txt.print_ub(a)
        txt.nl()
        txt.print_ub(func2())
        txt.nl()
        floats.print_f(floats.PI)
        sys.wait(60)
    }
}

;main33 {
;
;    sub calc(ubyte x, ubyte y) -> uword {
;        %ir {{
;            loadcpu.b r42,A
;            loadcpu.w r42,XY
;        }}
;        repeat x+y {
;            x++
;        }
;        when x {
;            1 -> y++
;            3 -> y++
;            else -> y++
;        }
;        y--
;        return x as uword * y
;    }
;    sub start()  {
;        txt.print_uw(calc(22, 33))
;    }
;}
