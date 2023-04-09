%import textio
%zeropage basicsafe
main {
    sub start() {
        cx16.r1=0
        for cx16.r0 in 0 to 10 {
            cx16.r1++
        }
        txt.print_uw(cx16.r1)
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
