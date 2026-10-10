bcd {
    %option merge, ignore_unused

    asmsub addb(byte a @D0, byte b @D1) clobbers(D1) -> byte @D0 {
        %asm {{
            andi.b  #$ef,ccr
            abcd    d1,d0
            rts
        }}
    }

    asmsub addub(ubyte a @D0, ubyte b @D1) clobbers(D1) -> ubyte @D0 {
        %asm {{
            andi.b  #$ef,ccr
            abcd    d1,d0
            rts
        }}
    }

    asmsub addw(word a @D0, word b @D1) clobbers(D1) -> word @D0 {
        %asm {{
            andi.b  #$ef,ccr
            abcd    d1,d0
            ror.w   #8,d0
            ror.w   #8,d1
            abcd    d1,d0
            ror.w   #8,d0
            rts
        }}
    }

    asmsub adduw(uword a @D0, uword b @D1) clobbers(D1) -> uword @D0 {
        %asm {{
            andi.b  #$ef,ccr
            abcd    d1,d0
            ror.w   #8,d0
            ror.w   #8,d1
            abcd    d1,d0
            ror.w   #8,d0
            rts
        }}
    }

    asmsub addl(long a @D0, long b @D1) clobbers(D1) -> long @D0 {
        %asm {{
            andi.b  #$ef,ccr
            abcd    d1,d0
            rept 3
                ror.l   #8,d0
                ror.l   #8,d1
                abcd    d1,d0
            endr
            ror.l   #8,d0
            rts
        }}
    }

    asmsub subb(byte a @D0, byte b @D1) clobbers(D1) -> byte @D0 {
        %asm {{
            andi.b  #$ef,ccr
            sbcd    d1,d0
            rts
        }}
    }

    asmsub subub(ubyte a @D0, ubyte b @D1) clobbers(D1) -> ubyte @D0 {
        %asm {{
            andi.b  #$ef,ccr
            sbcd    d1,d0
            rts
        }}
    }

    asmsub subuw(uword a @D0, uword b @D1) clobbers(D1) -> uword @D0 {
        %asm {{
            andi.b  #$ef,ccr
            sbcd    d1,d0
            ror.w   #8,d0
            ror.w   #8,d1
            sbcd    d1,d0
            ror.w   #8,d0
            rts
        }}
    }

    asmsub subl(long a @D0, long b @D1) clobbers(D1) -> long @D0 {
        %asm {{
            andi.b  #$ef,ccr
            sbcd    d1,d0
            rept 3
                ror.l   #8,d0
                ror.l   #8,d1
                sbcd    d1,d0
            endr
            ror.l   #8,d0
            rts
        }}
    }

    asmsub addtol(^^long a @A0, long b @D0) clobbers(D0,D1) {
        %asm {{
            move.l  (a0),d1
            andi.b  #$ef,ccr
            abcd    d0,d1
            rept 3
                ror.l   #8,d0
                ror.l   #8,d1
                abcd    d0,d1
            endr
            ror.l   #8,d1
            move.l  d1,(a0)
            rts
        }}
    }

    asmsub subfroml(^^long a @A0, long b @D0) clobbers(D0,D1) {
        %asm {{
            move.l  (a0),d1
            andi.b  #$ef,ccr
            sbcd    d0,d1
            rept 3
                ror.l   #8,d0
                ror.l   #8,d1
                sbcd    d0,d1
            endr
            ror.l   #8,d1
            move.l  d1,(a0)
            rts
        }}
    }
}
