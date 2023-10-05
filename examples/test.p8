%import diskio
%import textio
;%import math
;%import verafx
%zeropage basicsafe
%option no_sysinit

main {
    %option verafxmuls

    sub start() {
        word w1 = -123
        word w2 = 222
        ubyte b2 = 222
        byte sb2 = 111
        txt.print_w(w1*w2)
        txt.nl()
        txt.print_w(w1*222)
        txt.nl()
        w1 = -123
        w1 *= 222
        txt.print_w(w1)
        txt.nl()
        w1 = -123
        w1 *= w2
        txt.print_w(w1)
        txt.nl()
        w1 = -123
        w1 *= (w2-1)
        txt.print_w(w1)
        txt.nl()
        w1 = -123
        w1 *= b2
        txt.print_w(w1)
        txt.nl()
        w1 = -123
        w1 *= sb2
        txt.print_w(w1)
        txt.nl()

;        txt.print_uw(math.mul16_last_upper())
;        txt.nl()
;        uword value1=5678
;        uword value2=9999
;        uword result = value1*value2
;        uword upper16 = math.mul16_last_upper()
;        txt.print_uw(result)
;        txt.spc()
;        txt.print_uw(upper16)
;        txt.nl()


;        const word MULTIPLIER = 431
;
;        ; verify results:
;        for value in -50 to 50 {
;            if value*MULTIPLIER != verafx.muls(value, MULTIPLIER) {
;                txt.print("verafx muls error\n")
;                sys.exit(1)
;            }
;        }
;
;
;        word value
;        txt.print("verafx muls...")
;        cbm.SETTIM(0,0,0)
;        for value in -50 to 50 {
;            repeat 250 void verafx.muls(value, MULTIPLIER)
;        }
;        txt.print_uw(cbm.RDTIM16())
;        txt.nl()
;
;        txt.print("6502 muls...")
;        cbm.SETTIM(0,0,0)
;        for value in -50 to 50 {
;            repeat 250 cx16.r0s = value*MULTIPLIER
;        }
;        txt.print_uw(cbm.RDTIM16())
;        txt.nl()

    }
}

