%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared b1 = %10101010
        ubyte @shared b2 = %00001111

        b1 &= ~b2
        txt.print_ubbin(b1, true)
        txt.nl()
        b1 |= b2
        txt.print_ubbin(b1, true)
        txt.nl()

        b1 = %11001100
        b2 = %11110000

        b1 &= ~b2
        txt.print_ubbin(b1, true)
        txt.nl()
        b1 |= b2
        txt.print_ubbin(b1, true)
        txt.nl()

;        smallringbuffer.init()
;
;        smallringbuffer.put(123)
;        txt.print_ub(smallringbuffer.get())
;        txt.nl()
;
;        smallringbuffer.putw(12345)
;        txt.print_uw(smallringbuffer.getw())
;        txt.nl()
    }
}


;
;main {
;    sub start() {
;        signed()
;        unsigned()
;    }
;
;    sub signed() {
;        byte @shared bvalue = -100
;        word @shared wvalue = -20000
;
;        bvalue /= 2     ; TODO should be a simple bit shift?
;        wvalue /= 2     ; TODO should be a simple bit shift?
;
;        txt.print_b(bvalue)
;        txt.nl()
;        txt.print_w(wvalue)
;        txt.nl()
;
;        bvalue *= 2
;        wvalue *= 2
;
;        txt.print_b(bvalue)
;        txt.nl()
;        txt.print_w(wvalue)
;        txt.nl()
;    }
;
;    sub unsigned() {
;        ubyte @shared ubvalue = 100
;        uword @shared uwvalue = 20000
;
;        ubvalue /= 2
;        uwvalue /= 2
;
;        txt.print_ub(ubvalue)
;        txt.nl()
;        txt.print_uw(uwvalue)
;        txt.nl()
;
;        ubvalue *= 2
;        uwvalue *= 2
;
;        txt.print_ub(ubvalue)
;        txt.nl()
;        txt.print_uw(uwvalue)
;        txt.nl()
;    }
;}
