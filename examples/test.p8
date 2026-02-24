%import textio
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        ubyte @shared b1, b2, b3, b4
        b1, b2, b3 = multi()
    }

    sub multi() -> ubyte, ubyte, ubyte {
        return cx16.r0L, cx16.r1L, cx16.r2L
    }
}

