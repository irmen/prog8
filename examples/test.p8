%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {

    sub calculate(ubyte value) -> uword {
        when value {
            1 -> return "one"
            2 -> return "two"
            3 -> return "three"
            4,5,6 -> return "four to six"
            else -> return "other"
        }
    }

    sub start() {

        txt.print(calculate(0))
        txt.nl()
        txt.print(calculate(1))
        txt.nl()
        txt.print(calculate(2))
        txt.nl()
        txt.print(calculate(3))
        txt.nl()
        txt.print(calculate(4))
        txt.nl()
        txt.print(calculate(5))
        txt.nl()
        txt.print(calculate(50))
        txt.nl()

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
