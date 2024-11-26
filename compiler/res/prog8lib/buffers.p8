; **experimental** buffer data structures, API subject to change!!

%option no_symbol_prefixing, ignore_unused

smallringbuffer {
    ; -- A ringbuffer (FIFO queue) that occupies a single page in memory, containing 255 bytes maximum.
    ;    You can store and retrieve bytes and words too.
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
        return true
    }

    sub putw(uword value) -> bool {
        ; -- store a word in the buffer, returns success
        if fill>=254
            return false
        fill += 2
        buffer[head] = lsb(value)
        head++
        buffer[head] = msb(value)
        head++
        return true
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
        fill -= 2
        tail++
        cx16.r0L = buffer[tail]
        tail++
        cx16.r0H = buffer[tail]
        sys.set_carry()
        return cx16.r0
    }
}


ringbuffer {
    ; -- A ringbuffer (FIFO queue) that uses a block of 8 KB of memory.
    ;    You can store and retrieve bytes and words too.

    uword fill
    uword head
    uword tail
    uword buffer_ptr = memory("ringbuffer", 8192, 0)

    sub init() {
        ; -- (re)initialize the ringbuffer, you must call this before using the other routines
        head = fill = 0
        tail = 8191
    }

    sub put(ubyte value) -> bool {
        ; -- store a byte in the buffer, returns success
        if fill==8192
            return false
        buffer_ptr[head] = value
        inc_head()
        fill++
        return true
    }

    sub putw(uword value) -> bool {
        ; -- store a word in the buffer, returns success
        if fill>=8191
            return false
        fill += 2
        buffer_ptr[head] = lsb(value)
        inc_head()
        buffer_ptr[head] = msb(value)
        inc_head()
        return true
    }

    sub get() -> ubyte {
        ; -- retrieves a byte from the buffer. Also sets Carry flag: set=success, clear=buffer was empty
        if fill==0 {
            sys.clear_carry()
            return 0
        }
        fill--
        inc_tail()
        cx16.r0L = buffer_ptr[tail]
        sys.set_carry()
        return cx16.r0L
    }

    sub getw() -> uword {
        ; -- retrieves a word from the buffer. Also sets Carry flag: set=success, clear=buffer was empty
        if fill<2 {
            sys.clear_carry()
            return 0
        }
        fill -= 2
        inc_tail()
        cx16.r0L = buffer_ptr[tail]
        inc_tail()
        cx16.r0H = buffer_ptr[tail]
        sys.set_carry()
        return cx16.r0
    }

    sub inc_head() {
        head++
        if msb(head)==$20
            head=0
    }

    sub inc_tail() {
        tail++
        if msb(tail)==$20
            tail=0
    }
}


; TODO ringbuffer (FIFO queue) should use banked ram on the X16, but still work on virtual target
; TODO stack (LIFO queue) using more than 1 page of ram (maybe even banked ram on the x16)
