%import textio
%import math
%import floats
%import string

main {
    sub start() {
        str name = "irmen"
        reverse(name)
        txt.print(name)
        txt.nl()
        sort(name)
        txt.print(name)
        txt.nl()
        txt.print_ub('@' in name)
        txt.nl()
        txt.print_ub('i' in name)
        txt.nl()
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
