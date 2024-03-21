%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared flag

        cx16.r0L = test(12345, flag, -42)

    }

    asmsub test(uword arg @AY, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc {
        %asm {{
            txa
            rts
        }}
    }
}
