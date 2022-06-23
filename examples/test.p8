%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target vitual)

main  {

    sub funcFalse() -> ubyte {
        txt.print("false() ")
        return false
    }

    sub funcFalseWord() -> uword {
        txt.print("falseWord() ")
        return 0
    }

    sub funcTrue() -> ubyte {
        txt.print("ftrue() ")
        return true
    }

    sub func1(ubyte a1) -> ubyte {
        txt.print("func1() ")
        return a1>1
    }

    sub func2(ubyte a1) -> ubyte {
        txt.print("func2() ")
        return a1>2
    }

    sub func3(ubyte a1) -> ubyte {
        txt.print("func3() ")
        return a1>3
    }

    sub func4(ubyte a1) -> ubyte {
        txt.print("func4() ")
        return a1>4
    }

    sub funcw() -> uword {
        txt.print("funcw() ")
        return 9999
    }

    sub start() {
        ubyte value
        uword wvalue
        byte svalue = -99

        wvalue = 3*abs(svalue) +abs(svalue)+abs(svalue)+abs(svalue)
        txt.print_uw(wvalue)

        txt.print("short and with false (word): ")
        wvalue = funcw() and funcFalseWord() and funcw() and funcw()
        txt.print_uw(wvalue)
        txt.nl()

        txt.print("short and with false: ")
        value = func1(25) and funcFalse()
        txt.print_ub(value)
        txt.nl()
        txt.print("short or with true: ")
        value = func1(25) or funcTrue()
        txt.print_ub(value)
        txt.nl()
        txt.print("short xor with false: ")
        value = func1(25) xor funcFalse()
        txt.print_ub(value)
        txt.nl()

        txt.print("and with false: ")
        value = func1(25) and func2(25) and funcFalse() and func3(25) and func4(25)
        txt.print_ub(value)
        txt.nl()
        txt.print("and with true:  ")
        value = func1(25) and func2(25) and funcTrue() and func3(25) and func4(25)
        txt.print_ub(value)
        txt.nl()
        txt.print("or with false:  ")
        value = func1(25) or func2(25) or funcFalse() or func3(25) or func4(25)
        txt.print_ub(value)
        txt.nl()
        txt.print("or with true:   ")
        value = func1(25) or func2(25) or funcTrue() or func3(25) or func4(25)
        txt.print_ub(value)
        txt.nl()
        txt.print("xor with false: ")
        value = func1(25) xor func2(25) xor funcFalse() xor func3(25) xor func4(25)
        txt.print_ub(value)
        txt.nl()
        txt.print("xor with true:  ")
        value = func1(25) xor func2(25) xor funcTrue() xor func3(25) xor func4(25)
        txt.print_ub(value)
        txt.nl()


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
