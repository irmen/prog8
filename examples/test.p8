
%import textio
%import buffers
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        smallringbuffer.init()

        smallringbuffer.put(123)
        txt.print_ub(smallringbuffer.get())
        txt.nl()

        smallringbuffer.putw(12345)
        txt.print_uw(smallringbuffer.getw())
        txt.nl()
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
