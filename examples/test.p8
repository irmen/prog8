%zeropage basicsafe

main {

    sub start() {
        uword @shared wvar = 5

        if wvar <= 10
            cx16.r0++

        if wvar > 10
            cx16.r1++

        add2()
    }

    sub add2() {
        ^^uword @shared ptr = 0

        ptr += cx16.r0L
        cx16.r0 = ptr + cx16.r0L
        cx16.r0 = peekw(ptr + cx16.r0L)
    }
}
