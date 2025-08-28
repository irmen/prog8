main {
    sub start() {
        ^^uword @shared ptr

;        add1()
;        add2()
        sub1()
;        sub2()

        sub add1() {
            ptr += 5
            cx16.r0 = ptr + 5
            cx16.r0 = peekw(ptr + 5)
        }

        sub add2() {
            ptr += cx16.r0L
            cx16.r0 = ptr + cx16.r0L
            cx16.r0 = peekw(ptr + cx16.r0L)
        }

        sub sub1() {
            ptr -= 5
            cx16.r0 = ptr - 5
            cx16.r0 = peekw(ptr - 5)
        }

        sub sub2() {
            ptr -= cx16.r0L
            cx16.r0 = ptr - cx16.r0L
            cx16.r0 = peekw(ptr - cx16.r0L)
        }
    }
}
