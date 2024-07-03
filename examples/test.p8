
%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ringbuffer256.init()

        cx16.r0L = ringbuffer256.get()
        if_cs {
            txt.print_ub(cx16.r0L)
            txt.nl()
        } else {
            txt.print("buffer empty\n")
        }
    }
}

ringbuffer256 {
    uword size
    ubyte head
    ubyte tail
    ubyte[256] buffer

    sub init() {
        size = head = 0
        tail = 255
    }

    sub add(ubyte value) -> bool {
        if size==256
            return false

        buffer[head] = value
        head++
        size++
    }

    sub get() -> ubyte {
        if size==0 {
            sys.clear_carry()
            return 0
        }

        size--
        tail++
        sys.set_carry()
        return buffer[tail]
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
