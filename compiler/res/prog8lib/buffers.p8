; experimental buffer data structures

%option no_symbol_prefixing, ignore_unused

smallringbuffer {
    ; -- A ringbuffer (FIFO queue) that occupies a single page in memory, containing 255 bytes maximum.
    ;    You can store and retrieve words too.
    ;    It's optimized for speed and depends on the byte-wrap-around feature when doing incs and decs.

    ubyte fill
    ubyte head
    ubyte tail
    ubyte[256] buffer

    sub init() {
        ; -- (re)initialize the ringbuffer, you must call this before using the other routines
        head = fill = 0
        tail = 255
    }

    sub put(ubyte value) -> bool {
        ; -- store a byte in the buffer, returns success
        if fill==255
            return false
        buffer[head] = value
        head++
        fill++
    }

    sub putw(uword value) -> bool {
        ; -- store a word in the buffer, returns success
        if fill>=254
            return false
        fill+=2
        buffer[head] = lsb(value)
        head++
        buffer[head] = msb(value)
        head++
    }

    sub get() -> ubyte {
        ; -- retrieves a byte from the buffer. Also sets Carry flag: set=success, clear=buffer was empty
        if fill==0 {
            sys.clear_carry()
            return 0
        }
        fill--
        tail++
        sys.set_carry()
        return buffer[tail]
    }

    sub getw() -> uword {
        ; -- retrieves a word from the buffer. Also sets Carry flag: set=success, clear=buffer was empty
        if fill<2 {
            sys.clear_carry()
            return 0
        }
        fill-=2
        tail++
        cx16.r0L = buffer[tail]
        tail++
        cx16.r0H = buffer[tail]
        sys.set_carry()
        return cx16.r0
    }
}


; TODO ringbuffer (FIFO queue) using more than 1 page of ram (maybe even banked ram on the x16)
; TODO stack (LIFO queue) using more than 1 page of ram (maybe even banked ram on the x16)
