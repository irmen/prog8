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

    asmsub rndseed(uword seed1 @AY, uword seed2 @R0) clobbers(A,Y) {
        ; -- reset the pseudo RNG's seed values. Defaults are: $a55a, $7653.
        %asm {{
            sta  math.randword.sr1
            sty  math.randword.sr1+1
            lda  cx16.r0L
            ldy  cx16.r0H
            sta  math.randword.sr2
            sty  math.randword.sr2+1
            rts
        }}
    }
}
