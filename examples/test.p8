%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared flag

        cx16.r1=9999
        flag = test(42)
        cx16.r0L, flag = test2(12345, 5566, flag, -42)
        cx16.r0, flag = test3()
    }

    asmsub test(ubyte arg @A) -> bool @Pc {
        %asm {{
            sec
            rts
        }}
    }

    ; TODO  cx16.r1 as return reg

    asmsub test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc {
        %asm {{
            txa
            sec
            rts
        }}
    }

    asmsub test3() -> uword @AY, bool @Pc {
        %asm {{
            lda  #0
            ldy  #0
            rts
        }}
    }
}
