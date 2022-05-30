; Internal Math library routines - always included by the compiler

math {
	%asminclude "library:math.asm"

    asmsub sin8u(ubyte angle @A) clobbers(Y) -> ubyte @A {
        %asm {{
		tay
		lda  _sinecos8u,y
		rts
_sinecos8u	.byte  trunc(128.0 + 127.5 * sin(range(256+64) * rad(360.0/256.0)))
        }}
    }

    asmsub cos8u(ubyte angle @A) clobbers(Y) -> ubyte @A {
        %asm {{
		tay
		lda  sin8u._sinecos8u+64,y
		rts
        }}
    }

    asmsub sin8(ubyte angle @A) clobbers(Y) -> byte @A {
        %asm {{
		tay
		lda  _sinecos8,y
		rts
_sinecos8	.char  trunc(127.0 * sin(range(256+64) * rad(360.0/256.0)))
        }}
    }

    asmsub cos8(ubyte angle @A) clobbers(Y) -> byte @A {
        %asm {{
		tay
		lda  sin8._sinecos8+64,y
		rts
        }}
    }

    asmsub sin16(ubyte angle @A) -> word @AY {
        %asm {{
		tay
		lda  _sinecos8lo,y
		pha
		lda  _sinecos8hi,y
		tay
		pla
		rts
_  :=  trunc(32767.0 * sin(range(256+64) * rad(360.0/256.0)))
_sinecos8lo     .byte  <_
_sinecos8hi     .byte  >_
        }}
    }

    asmsub cos16(ubyte angle @A) -> word @AY {
        %asm {{
		tay
		lda  sin16._sinecos8lo+64,y
		pha
		lda  sin16._sinecos8hi+64,y
		tay
		pla
		rts
        }}
    }

    asmsub sin16u(ubyte angle @A) -> uword @AY {
        %asm {{
		tay
		lda  _sinecos8ulo,y
		pha
		lda  _sinecos8uhi,y
		tay
		pla
		rts
_  :=  trunc(32768.0 + 32767.5 * sin(range(256+64) * rad(360.0/256.0)))
_sinecos8ulo     .byte  <_
_sinecos8uhi     .byte  >_
        }}
    }

    asmsub cos16u(ubyte angle @A) -> uword @AY {
        %asm {{
		tay
		lda  sin16u._sinecos8ulo+64,y
		pha
		lda  sin16u._sinecos8uhi+64,y
		tay
		pla
		rts
        }}
    }

    asmsub sinr8u(ubyte radians @A) clobbers(Y) -> ubyte @A {
        %asm {{
		tay
		lda  _sinecosR8u,y
		rts
_sinecosR8u	.byte  trunc(128.0 + 127.5 * sin(range(180+45) * rad(360.0/180.0)))
        }}
    }

    asmsub cosr8u(ubyte radians @A) clobbers(Y) -> ubyte @A {
        %asm {{
		tay
		lda  sinr8u._sinecosR8u+45,y
		rts
        }}
    }

    asmsub sinr8(ubyte radians @A) clobbers(Y) -> byte @A {
        %asm {{
		tay
		lda  _sinecosR8,y
		rts
_sinecosR8	.char  trunc(127.0 * sin(range(180+45) * rad(360.0/180.0)))
        }}
    }

    asmsub cosr8(ubyte radians @A) clobbers(Y) -> byte @A {
        %asm {{
		tay
		lda  sinr8._sinecosR8+45,y
		rts
        }}
    }

    asmsub sinr16(ubyte radians @A) -> word @AY {
        %asm {{
		tay
		lda  _sinecosR8lo,y
		pha
		lda  _sinecosR8hi,y
		tay
		pla
		rts
_  :=  trunc(32767.0 * sin(range(180+45) * rad(360.0/180.0)))
_sinecosR8lo     .byte  <_
_sinecosR8hi     .byte  >_
        }}
    }

    asmsub cosr16(ubyte radians @A) -> word @AY {
        %asm {{
		tay
		lda  sinr16._sinecosR8lo+45,y
		pha
		lda  sinr16._sinecosR8hi+45,y
		tay
		pla
		rts
        }}
    }

    asmsub sinr16u(ubyte radians @A) -> uword @AY {
        %asm {{
		tay
		lda  _sinecosR8ulo,y
		pha
		lda  _sinecosR8uhi,y
		tay
		pla
		rts
_  :=  trunc(32768.0 + 32767.5 * sin(range(180+45) * rad(360.0/180.0)))
_sinecosR8ulo     .byte  <_
_sinecosR8uhi     .byte  >_
        }}
    }

    asmsub cosr16u(ubyte radians @A) -> uword @AY {
        %asm {{
		tay
		lda  sinr16u._sinecosR8ulo+45,y
		pha
		lda  sinr16u._sinecosR8uhi+45,y
		tay
		pla
		rts
        }}
    }
}
