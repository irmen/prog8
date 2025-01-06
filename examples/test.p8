%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        cx16.r0,cx16.r1 = single()
        cx16.r0 = multi()
    }

    sub single() -> uword {
        return 42+cx16.r0L
    }

    sub multi() -> uword, uword {
        return 42+cx16.r0L, 99
    }
}
