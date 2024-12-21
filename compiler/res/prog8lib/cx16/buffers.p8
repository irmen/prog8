; **experimental** buffer data structures, API subject to change!!

%option ignore_unused

smallringbuffer {
    ; -- A ringbuffer (FIFO queue) that occupies 256 bytes in memory.
    ;    You can store and retrieve bytes and words. No guards against over/underflow.
    ;    It's optimized for speed and depends on the byte-wrap-around when doing incs and decs.

    ubyte fill = 0
    ubyte head = 0
    ubyte tail = 255
    ubyte[256] buffer

    sub init() {
        ; -- (re)initialize the ringbuffer
        head = fill = 0
        tail = 255
    }

    sub size() -> ubyte {
        return fill
    }

    sub free() -> ubyte {
        return 255-fill
    }

    sub isfull() -> bool {
        return fill>=254
    }

    sub isempty() -> bool {
        return fill<=1
    }

    sub put(ubyte value) {
        ; -- store a byte in the buffer
        buffer[head] = value
        head++
        fill++
    }

    sub putw(uword value) {
        ; -- store a word in the buffer
        fill += 2
        buffer[head] = lsb(value)
        head++
        buffer[head] = msb(value)
        head++
    }

    sub get() -> ubyte {
        ; -- retrieves a byte from the buffer
        fill--
        tail++
        return buffer[tail]
    }

    sub getw() -> uword {
        ; -- retrieves a word from the buffer
        fill -= 2
        tail++
        cx16.r0L = buffer[tail]
        tail++
        cx16.r0H = buffer[tail]
        return cx16.r0
    }
}

; note: for a "small stack"  (256 bytes size) just use the CPU stack via sys.push[w] / sys.pop[w].

stack {
    ; -- A stack (LIFO) that uses a block of 8 KB of memory. Growing downward from the top of the buffer.
    ;    You can store and retrieve bytes and words. There are no guards against stack over/underflow.

    uword sp
    ubyte bank

    sub init(ubyte rambank) {
        ; -- initialize the stack, must be called before use. Supply the HiRAM bank to use as buffer space.
        sp = $bfff
        bank = rambank
    }

    sub size() -> uword {
        return $bfff-sp
    }

    sub free() -> uword {
        return sp-$a000
    }

    sub isfull() -> bool {
        return sp<=$a001
    }

    sub isempty() -> bool {
        return sp==$bfff
    }

    sub push(ubyte value) {
        ; -- put a byte on the stack
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        @(sp) = value
        sp--

        cx16.rambank(sys.pop())
    }

    sub pushw(uword value) {
        ; -- put a word on the stack (lsb first then msb)
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        @(sp) = lsb(value)
        sp--
        @(sp) = msb(value)
        sp--

        cx16.rambank(sys.pop())
    }

    sub pop() -> ubyte {
        ; -- pops a byte off the stack
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        sp++
        cx16.r0L = @(sp)
        cx16.rambank(sys.pop())
        return cx16.r0L
    }

    sub popw() -> uword {
        ; -- pops a word off the stack.
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        sp++
        cx16.r0H = @(sp)
        sp++
        cx16.r0L = @(sp)
        cx16.rambank(sys.pop())
        return cx16.r0
    }
}


ringbuffer {
    ; -- A ringbuffer (FIFO queue) that uses a block of 8 KB of memory.
    ;    You can store and retrieve bytes and words too. No guards against buffer under/overflow.

    uword fill, head, tail
    ubyte bank = 255        ; set via init()

    sub init(ubyte rambank) {
        ; -- initialize the ringbuffer, must be called before use. Supply the HiRAM bank to use as buffer space.
        head = $a000
        tail = $bfff
        fill = 0
        bank = rambank
    }

    sub size() -> uword {
        return fill
    }

    sub free() -> uword {
        return $1fff-fill
    }

    sub isempty() -> bool {
        return fill<=1
    }

    sub isfull() -> bool {
        return fill>=8191
    }

    sub put(ubyte value) {
        ; -- store a byte in the buffer
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        @(head) = value
        fill++
        inc_head()
        cx16.rambank(sys.pop())
    }

    sub putw(uword value) {
        ; -- store a word in the buffer
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        pokew(head, value)
        fill += 2
        inc_head()
        inc_head()
        cx16.rambank(sys.pop())
    }

    sub get() -> ubyte {
        ; -- retrieves a byte from the buffer
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        fill--
        inc_tail()
        cx16.r0L= @(tail)
        cx16.rambank(sys.pop())
        return cx16.r0L
    }

    sub getw() -> uword {
        ; -- retrieves a word from the buffer
        sys.push(cx16.getrambank())
        cx16.rambank(bank)

        fill -= 2
        inc_tail()
        cx16.r0L = @(tail)
        inc_tail()
        cx16.r0H = @(tail)
        cx16.rambank(sys.pop())
        return cx16.r0
    }

    sub inc_head() {
        head++
        if msb(head)==$c0
            head=$a000
    }

    sub inc_tail() {
        tail++
        if msb(tail)==$c0
            tail=$a000
    }
}

