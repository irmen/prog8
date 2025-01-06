%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        cx16.r0L, cx16.r1, cx16.r2 = multiasm()
        cx16.r0L = multiasm()
    }

    asmsub multiasm() -> ubyte @A, uword @R1 {
        %asm {{
            rts
        }}
    }
}
