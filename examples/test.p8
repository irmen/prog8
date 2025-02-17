%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        const uword MAX_CAVE_WIDTH = 440             ; word here to avoid having to cast to word all the time

        if cx16.r0L > MAX_CAVE_WIDTH
            return
    }
}
