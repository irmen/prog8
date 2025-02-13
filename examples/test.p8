%zeropage basicsafe
%option no_sysinit


main {

    sub start() {
        const uword x=128

        const uword MAX_CAVE_WIDTH = 40             ; word here to avoid having to cast to word all the time
        ubyte @shared width

        if cx16.r0L==x
            return
        if cx16.r0L>x
            return
        if cx16.r0L<x
            return

        if width<3 or width>MAX_CAVE_WIDTH      ; TODO optimize this too
            return

        cx16.r0L = x        ; already gets optimized to a byte value
    }
}
