%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        cx16.r0 = single()
        void multi()
        cx16.r0,void = multi()
        cx16.r0,cx16.r1 = multi()
    }

    sub single() -> uword {
        return 42+cx16.r0L
    }

    sub multi() -> uword, uword {
        defer cx16.r0++
        return 42+cx16.r0L, 99
    }
}
