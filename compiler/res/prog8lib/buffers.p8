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


smallstack {
    ; -- A small stack (LIFO) that uses just 256 bytes and is independent of the CPU stack. Stack is growing downward from the top of the buffer.
    ;    You can store and retrieve bytes and words. There are no guards against stack over/underflow.
    ; note: for a "small stack"  (256 bytes size) you might also perhaps just use the CPU stack via sys.push[w] / sys.pop[w].

    ubyte[256] buffer
    ubyte sp = 255

    sub init() {
        sp = 255
    }

    sub size() -> ubyte {
        return 255-sp
    }

    sub free() -> ubyte {
        return sp
    }

    sub isfull() -> bool {
        return sp==0
    }

    sub isempty() -> bool {
        return sp==255
    }

    sub push(ubyte value) {
        ; -- put a byte on the stack
        buffer[sp] = value
        sp--
    }

    sub pushw(uword value) {
        ; -- put a word on the stack (lsb first then msb)
        buffer[sp] = lsb(value)
        sp--
        buffer[sp] = msb(value)
        sp--
    }

    sub pop() -> ubyte {
        ; -- pops a byte off the stack
        sp++
        return buffer[sp]
    }

    sub popw() -> uword {
        ; -- pops a word off the stack.
        sp++
        cx16.r0H = buffer[sp]
        sp++
        cx16.r0L = buffer[sp]
        return cx16.r0
    }
}


stack {
    ; -- A stack (LIFO) that uses a block of 8 KB of memory. Growing downward from the top of the buffer.
    ;    You can store and retrieve bytes and words. There are no guards against stack over/underflow.

    uword sp = 8191
    uword buffer_ptr = memory("buffers_stack", 8192, 0)

    sub init() {
        sp = 8191
    }

    sub size() -> uword {
        return 8191-sp
    }

    sub free() -> uword {
        return sp
    }

    sub isfull() -> bool {
        return sp==0
    }

    sub isempty() -> bool {
        return sp==8191
    }

    sub push(ubyte value) {
        ; -- put a byte on the stack
        buffer_ptr[sp] = value
        sp--
    }

    sub pushw(uword value) {
        ; -- put a word on the stack (lsb first then msb)
        buffer_ptr[sp] = lsb(value)
        sp--
        buffer_ptr[sp] = msb(value)
        sp--
    }

    sub pop() -> ubyte {
        ; -- pops a byte off the stack
        sp++
        return buffer_ptr[sp]
    }

    sub popw() -> uword {
        ; -- pops a word off the stack.
        sp++
        cx16.r0H = buffer_ptr[sp]
        sp++
        cx16.r0L = buffer_ptr[sp]
        return cx16.r0
    }
}


ringbuffer {
    ; -- A ringbuffer (FIFO queue) that uses a block of 8 KB of memory.
    ;    You can store and retrieve bytes and words too. No guards against buffer under/overflow.

    uword fill = 0
    uword head = 0
    uword tail = 8191
    uword buffer_ptr = memory("buffers_ringbuffer", 8192, 0)

    sub init() {
        head = fill = 0
        tail = 8191
    }

    sub size() -> uword {
        return fill
    }

    sub free() -> uword {
        return 8191-fill
    }

    sub isempty() -> bool {
        return fill==0
    }

    sub isfull() -> bool {
        return fill>=8191
    }

    sub put(ubyte value) {
        ; -- store a byte in the buffer
        buffer_ptr[head] = value
        inc_head()
        fill++
    }

    sub putw(uword value) {
        ; -- store a word in the buffer
        fill += 2
        buffer_ptr[head] = lsb(value)
        inc_head()
        buffer_ptr[head] = msb(value)
        inc_head()
    }

    sub get() -> ubyte {
        ; -- retrieves a byte from the buffer
        fill--
        inc_tail()
        return buffer_ptr[tail]
    }

    sub getw() -> uword {
        ; -- retrieves a word from the buffer
        fill -= 2
        inc_tail()
        cx16.r0L = buffer_ptr[tail]
        inc_tail()
        cx16.r0H = buffer_ptr[tail]
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

