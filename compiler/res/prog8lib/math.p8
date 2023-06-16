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

    asmsub rnd() -> ubyte @A {
        %asm {{
            jmp  math.randbyte
        }}
    }

    asmsub rndw() -> uword @AY {
        %asm {{
            jmp  math.randword
        }}
    }

    asmsub rndseed(uword seed1 @AY, uword seed2 @R0) clobbers(A,Y) {
        ; -- set new pseudo RNG's seed values. Defaults are: $00c2, $1137
        %asm {{
            sta  math.randword.x1
            sty  math.randword.c1
            lda  cx16.r0L
            sta  math.randword.a1
            lda  cx16.r0H
            sta  math.randword.b1
            rts
        }}
    }


sub atan_coarse_sgn(byte x1, byte y1, byte x2, byte y2) -> ubyte {
    ; From a pair of signed coordinates around the origin, calculate discrete direction between 0 and 23 into A.
    cx16.r0L = 3        ; quadrant
    cx16.r1sL = x2-x1   ; xdelta
    if_neg {
        cx16.r0L--
        cx16.r1sL = -cx16.r1sL
    }
    cx16.r2sL = y2-y1   ; ydelta
    if_neg {
        cx16.r0L-=2
        cx16.r2sL = -cx16.r2sL
    }
    return atan_coarse_qd(cx16.r0L, cx16.r1L, cx16.r2L)
}

sub atan_coarse(ubyte x1, ubyte y1, ubyte x2, ubyte y2) -> ubyte {
    ; From a pair of positive coordinates, calculate discrete direction between 0 and 23 into A.
    cx16.r0L = 3        ; quadrant
    if x2>=x1 {
        cx16.r1L = x2-x1
    } else {
        cx16.r1L = x1-x2
        cx16.r0L--
    }
    if y2>=y1 {
        cx16.r2L = y2-y1
    } else {
        cx16.r2L = y1-y2
        cx16.r0L -= 2
    }
    return atan_coarse_qd(cx16.r0L, cx16.r1L, cx16.r2L)
}

asmsub atan_coarse_qd(ubyte quadrant @A, ubyte xdelta @X, ubyte ydelta @Y) -> ubyte @A {
    ;Arctan  https://github.com/dustmop/arctan24
    ; From a pair of X/Y deltas (both >=0), and quadrant 0-3, calculate discrete direction between 0 and 23 into A.
    ;  .reg:a @in  quadrant Number 0 to 3.
    ;  .reg:x @in  x_delta Delta for x direction.
    ;  .reg:y @in  y_delta Delta for y direction.
    ; Returns A as the direction (0-23).

    %asm {{
x_delta = cx16.r0L
y_delta = cx16.r1L
quadrant = cx16.r2L
half_value = cx16.r3L
region_number = cx16.r4L
small = cx16.r5L
large = cx16.r5H

  sta quadrant
  sty y_delta
  stx x_delta
  cpx y_delta
  bcs _XGreaterOrEqualY

_XLessY:
  lda #16
  sta region_number
  stx small
  sty large
  bne _DetermineRegion

_XGreaterOrEqualY:
  lda #0
  sta region_number
  stx large
  sty small

_DetermineRegion:
  ; set A = small * 2.5
  lda small
  lsr a
  sta half_value
  lda small
  asl a
  bcs _SmallerQuotient
  clc
  adc half_value
  bcs _SmallerQuotient
  cmp large
  bcc _LargerQuotient

; S * 2.5 > L
_SmallerQuotient:
  ; set A = S * 1.25
  lsr half_value
  lda small
  clc
  adc half_value
  cmp large
  bcc _Region1 ; if S * 1.25 < L then goto Region1 (L / S > 1.25)
  bcs _Region0 ;                                   (L / S < 1.25)

; S * 2.5 < L
_LargerQuotient:
  ; set A = S * 7.5
  lda small
  asl a
  asl a
  asl a
  bcs _Region2
  sec
  sbc half_value
  cmp large
  bcc _Region3 ; if S * 7.5 < L then goto Region3 (L / S > 7.5)
  jmp _Region2 ;                                  (L / S < 7.5)

_Region0:
  ; L / S < 1.25. d=3,9,15,21
  jmp _LookupResult

_Region1:
  ; 1.25 < L / S < 2.5. d=2,4,8,10,14,16,20,22
  lda region_number
  clc
  adc #4
  sta region_number
  bpl _LookupResult

_Region2:
  ; 2.5 < L / S < 7.5. d=1,5,7,11,13,17,19,23
  lda region_number
  clc
  adc #8
  sta region_number
  bpl _LookupResult

_Region3:
  ; 7.5 < L / S. d=0,6,12,18
  lda region_number
  clc
  adc #12
  sta region_number

_LookupResult:
  lda quadrant
  clc
  adc region_number
  tax
  lda _quadrant_region_to_direction,x
  rts

_quadrant_region_to_direction:
  .byte  9, 3,15,21
  .byte 10, 2,14,22
  .byte 11, 1,13,23
  .byte 12, 0,12, 0
  .byte  9, 3,15,21
  .byte  8, 4,16,20
  .byte  7, 5,17,19
  .byte  6, 6,18,18

    }}
}

asmsub atan(ubyte x1 @R0, ubyte y1 @R1, ubyte x2 @R2, ubyte y2 @R3) -> ubyte @A {
    ;; Calculate the angle, in a 256-degree circle, between two points into A.
    ;; The points (x1, y1) and (x2, y2) have to use *unsigned coordinates only* from the positive quadrant in the carthesian plane!
    ;; https://www.codebase64.org/doku.php?id=base:8bit_atan2_8-bit_angle
    ;; This uses 2 large lookup tables so uses a lot of memory but is super fast.

    %asm {{

x1 = cx16.r0L
y1 = cx16.r1L
x2 = cx16.r2L
y2 = cx16.r3L
octant = cx16.r4L			;; temporary zeropage variable

		lda x1
		sbc x2
		bcs *+4
		eor #$ff
		tax
		rol octant

		lda y1
		sbc y2
		bcs *+4
		eor #$ff
		tay
		rol octant

		lda log2_tab,x
		sbc log2_tab,y
		bcc *+4
		eor #$ff
		tax

		lda octant
		rol a
		and #%111
		tay

		lda atan_tab,x
		eor octant_adjust,y
		rts

octant_adjust
		.byte %00111111		;; x+,y+,|x|>|y|
		.byte %00000000		;; x+,y+,|x|<|y|
		.byte %11000000		;; x+,y-,|x|>|y|
		.byte %11111111		;; x+,y-,|x|<|y|
		.byte %01000000		;; x-,y+,|x|>|y|
		.byte %01111111		;; x-,y+,|x|<|y|
		.byte %10111111		;; x-,y-,|x|>|y|
		.byte %10000000		;; x-,y-,|x|<|y|


		;;;;;;;; atan(2^(x/32))*128/pi ;;;;;;;;

atan_tab
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$00,$00,$00
		.byte $00,$00,$00,$00,$00,$01,$01,$01
		.byte $01,$01,$01,$01,$01,$01,$01,$01
		.byte $01,$01,$01,$01,$01,$01,$01,$01
		.byte $01,$01,$01,$01,$01,$01,$01,$01
		.byte $01,$01,$01,$01,$01,$02,$02,$02
		.byte $02,$02,$02,$02,$02,$02,$02,$02
		.byte $02,$02,$02,$02,$02,$02,$02,$02
		.byte $03,$03,$03,$03,$03,$03,$03,$03
		.byte $03,$03,$03,$03,$03,$04,$04,$04
		.byte $04,$04,$04,$04,$04,$04,$04,$04
		.byte $05,$05,$05,$05,$05,$05,$05,$05
		.byte $06,$06,$06,$06,$06,$06,$06,$06
		.byte $07,$07,$07,$07,$07,$07,$08,$08
		.byte $08,$08,$08,$08,$09,$09,$09,$09
		.byte $09,$0a,$0a,$0a,$0a,$0b,$0b,$0b
		.byte $0b,$0c,$0c,$0c,$0c,$0d,$0d,$0d
		.byte $0d,$0e,$0e,$0e,$0e,$0f,$0f,$0f
		.byte $10,$10,$10,$11,$11,$11,$12,$12
		.byte $12,$13,$13,$13,$14,$14,$15,$15
		.byte $15,$16,$16,$17,$17,$17,$18,$18
		.byte $19,$19,$19,$1a,$1a,$1b,$1b,$1c
		.byte $1c,$1c,$1d,$1d,$1e,$1e,$1f,$1f


		;;;;;;;; log2(x)*32 ;;;;;;;;

log2_tab
		.byte $00,$00,$20,$32,$40,$4a,$52,$59
		.byte $60,$65,$6a,$6e,$72,$76,$79,$7d
		.byte $80,$82,$85,$87,$8a,$8c,$8e,$90
		.byte $92,$94,$96,$98,$99,$9b,$9d,$9e
		.byte $a0,$a1,$a2,$a4,$a5,$a6,$a7,$a9
		.byte $aa,$ab,$ac,$ad,$ae,$af,$b0,$b1
		.byte $b2,$b3,$b4,$b5,$b6,$b7,$b8,$b9
		.byte $b9,$ba,$bb,$bc,$bd,$bd,$be,$bf
		.byte $c0,$c0,$c1,$c2,$c2,$c3,$c4,$c4
		.byte $c5,$c6,$c6,$c7,$c7,$c8,$c9,$c9
		.byte $ca,$ca,$cb,$cc,$cc,$cd,$cd,$ce
		.byte $ce,$cf,$cf,$d0,$d0,$d1,$d1,$d2
		.byte $d2,$d3,$d3,$d4,$d4,$d5,$d5,$d5
		.byte $d6,$d6,$d7,$d7,$d8,$d8,$d9,$d9
		.byte $d9,$da,$da,$db,$db,$db,$dc,$dc
		.byte $dd,$dd,$dd,$de,$de,$de,$df,$df
		.byte $df,$e0,$e0,$e1,$e1,$e1,$e2,$e2
		.byte $e2,$e3,$e3,$e3,$e4,$e4,$e4,$e5
		.byte $e5,$e5,$e6,$e6,$e6,$e7,$e7,$e7
		.byte $e7,$e8,$e8,$e8,$e9,$e9,$e9,$ea
		.byte $ea,$ea,$ea,$eb,$eb,$eb,$ec,$ec
		.byte $ec,$ec,$ed,$ed,$ed,$ed,$ee,$ee
		.byte $ee,$ee,$ef,$ef,$ef,$ef,$f0,$f0
		.byte $f0,$f1,$f1,$f1,$f1,$f1,$f2,$f2
		.byte $f2,$f2,$f3,$f3,$f3,$f3,$f4,$f4
		.byte $f4,$f4,$f5,$f5,$f5,$f5,$f5,$f6
		.byte $f6,$f6,$f6,$f7,$f7,$f7,$f7,$f7
		.byte $f8,$f8,$f8,$f8,$f9,$f9,$f9,$f9
		.byte $f9,$fa,$fa,$fa,$fa,$fa,$fb,$fb
		.byte $fb,$fb,$fb,$fc,$fc,$fc,$fc,$fc
		.byte $fd,$fd,$fd,$fd,$fd,$fd,$fe,$fe
		.byte $fe,$fe,$fe,$ff,$ff,$ff,$ff,$ff

    }}
}

}
