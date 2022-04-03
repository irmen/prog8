%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {

    sub func1(ubyte arg1) -> uword {
        return arg1 * 3
    }

    sub func2(uword arg1) -> uword {
        return arg1+1000
    }

    sub func3(uword arg1) {
        txt.print_uw(arg1+2000)
        txt.nl()
    }

    sub start() {

        ubyte source = 99

        uword result = func2(func1(cos8u(sin8u(source))))
        txt.print_uw(result)    ; 1043
        txt.nl()
        result = source |> sin8u() |> cos8u() |> func1() |> func2()
        txt.print_uw(result)    ; 1043
        txt.nl()
        source |> sin8u() |> cos8u() |> func1() |> func3()      ; 2043

        ; a "pixelshader":
;        syscall1(8, 0)      ; enable lo res creen
;        ubyte shifter
;
;        shifter >>= 1
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
;
;            txt.print_ub(shifter)
;            txt.nl()
;        }
    }
}
