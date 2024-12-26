%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool bb
        ubyte ub
        uword uw
        uw, void = thing2()
        uw, bb = thing2()
        uw, ub = thing2()
    }

    asmsub thing2() -> ubyte @A, bool @Pc {
        %asm {{
            lda #$aa
            clc
            rts
        }}
    }
}
