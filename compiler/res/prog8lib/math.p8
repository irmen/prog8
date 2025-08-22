; Internal Math library routines - always included by the compiler
; note: some functions you might expect here are builtin functions,
;       such as abs, sqrt, clamp, min, max for example.

math {
    %option no_symbol_prefixing, ignore_unused

    asmsub sin8u(ubyte angle @A) clobbers(Y) -> ubyte @A {
        %asm {{
		tay
		lda  _sinecos8u,y
		rts
_sinecos8u	.byte  trunc(128.0 + 127.5 * sin(range(256+64) * rad(360.0/256.0)))
        ; !notreached!
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
        ; !notreached!
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
        ; !notreached!
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
        ; !notreached!
        }}
    }

    asmsub cosr8(ubyte radians @A) clobbers(Y) -> byte @A {
        %asm {{
		tay
		lda  sinr8._sinecosR8+45,y
		rts
        }}
    }

    asmsub rnd() clobbers(Y) -> ubyte @A {
        %asm {{
            jmp  prog8_math.randbyte
        }}
    }

    asmsub rnd_rom() clobbers(Y) -> ubyte @A {
        %asm {{
            jmp  prog8_math.randbyte_rom
        }}
    }

    asmsub rndw() -> uword @AY {
        %asm {{
            jmp  prog8_math.randword
        }}
    }

    asmsub rndw_rom() -> uword @AY {
        %asm {{
            jmp  prog8_math.randword_rom
        }}
    }

    sub randrange(ubyte n) -> ubyte {
        ; -- return random number uniformly distributed from 0 to n-1
        ;    NOTE: does not work for code in ROM, use randrange_rom instead for that
        ; why this works: https://www.youtube.com/watch?v=3DvlLUWTNMY&t=347s
        cx16.r0 = math.rnd() * (n as uword)
        return cx16.r0H
    }

    sub randrange_rom(ubyte n) -> ubyte {
        ; -- return random number uniformly distributed from 0 to n-1
        ;    NOTE: works for code in ROM, make sure you have initialized the seed using rndseed_rom
        ; why this works: https://www.youtube.com/watch?v=3DvlLUWTNMY&t=347s
        cx16.r0 = math.rnd_rom() * (n as uword)
        return cx16.r0H
    }

    sub randrangew(uword n) -> uword {
        ; -- return random number uniformly distributed from 0 to n-1
        ;    NOTE: does not work for code in ROM
        ; why this works: https://www.youtube.com/watch?v=3DvlLUWTNMY&t=347s
        cx16.r0 = math.rndw() * n
        return math.mul16_last_upper()
    }

    sub randrangew_rom(uword n) -> uword {
        ; -- return random number uniformly distributed from 0 to n-1
        ;    NOTE: works for code in ROM, make sure you have initialized the seed using rndseed_rom
        ; why this works: https://www.youtube.com/watch?v=3DvlLUWTNMY&t=347s
        cx16.r0 = math.rndw_rom() * n
        return math.mul16_last_upper()
    }

    asmsub rndseed(uword seed1 @AY, uword seed2 @R0) clobbers(A,Y) {
        ; -- set new pseudo RNG's seed values. Defaults are: $00c2, $1137. NOTE: does not work for code in ROM, use rndseed_rom instead
        %asm {{
            sta  prog8_math.randword.x1
            sty  prog8_math.randword.c1
            lda  cx16.r0L
            sta  prog8_math.randword.a1
            lda  cx16.r0H
            sta  prog8_math.randword.b1
            rts
        }}
    }

    asmsub rndseed_rom(uword seed1 @AY, uword seed2 @R0) clobbers(A,Y) {
        ; -- set new pseudo RNG's seed values (for the ROM-version of the RNG). Good defaults are: $00c2, $1137.
        %asm {{
            sta  prog8_math.randword_rom._x1
            sty  prog8_math.randword_rom._c1
            lda  cx16.r0L
            sta  prog8_math.randword_rom._a1
            lda  cx16.r0H
            sta  prog8_math.randword_rom._b1
            rts
        }}
    }

    asmsub log2(ubyte value @A) -> ubyte @Y {
        %asm {{
            sta  P8ZP_SCRATCH_B1
            lda  #$80
            ldy  #7
-           bit  P8ZP_SCRATCH_B1
            beq  +
            rts
+           dey
            bne  +
            rts
+           lsr  a
            bne  -
            ; !notreached!
        }}
    }

    asmsub log2w(uword value @AY) -> ubyte @Y {
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            lda  #<$8000
            sta  cx16.r0
            lda  #>$8000
            sta  cx16.r0+1
            ldy  #15
-           lda  P8ZP_SCRATCH_W1
            and  cx16.r0
            sta  P8ZP_SCRATCH_B1
            lda  P8ZP_SCRATCH_W1+1
            and  cx16.r0+1
            ora  P8ZP_SCRATCH_B1
            beq  +
            rts
+           dey
            bne  +
            rts
+           lsr  cx16.r0+1
            ror  cx16.r0
            jmp  -
        }}
    }

    asmsub mul16_last_upper() -> uword @AY {
        ; This routine peeks into the internal 32 bits multiplication result buffer of the
        ; 16*16 bits multiplication routine, to fetch the upper 16 bits of the last calculation.
        ; Notes:
        ;   - to avoid interference it's best to fetch and store this value immediately after the multiplication expression.
        ;     for instance, simply printing a number may already result in new multiplication calls being performed
        ;   - not all multiplications in the source code result in an actual multiplication call:
        ;     some simpler multiplications will be optimized away into faster routines. These will not set the upper 16 bits at all!
        ;   - THE RESULT IS ONLY VALID IF THE MULTIPLICATION WAS DONE WITH UWORD ARGUMENTS (or two positive WORD arguments)
        ;     as soon as a negative word value (or 2) was used in the multiplication, these upper 16 bits are not valid!!
        ;     Suggestion (if you are on the Commander X16): use verafx.muls() to get a hardware accelerated 32 bit signed multplication.
        %asm {{
            lda  prog8_math.multiply_words.result+2
            ldy  prog8_math.multiply_words.result+3
            rts
        }}
    }

sub direction_sc(byte x1, byte y1, byte x2, byte y2) -> ubyte {
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
    return direction_qd(cx16.r0L, cx16.r1L, cx16.r2L)
}

sub direction(ubyte x1, ubyte y1, ubyte x2, ubyte y2) -> ubyte {
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
    return direction_qd(cx16.r0L, cx16.r1L, cx16.r2L)
}

asmsub direction_qd(ubyte quadrant @A, ubyte xdelta @X, ubyte ydelta @Y) -> ubyte @A {
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
        ; !notreached!
    }}
}

asmsub atan2(ubyte x1 @R0, ubyte y1 @R1, ubyte x2 @R2, ubyte y2 @R3) -> ubyte @A {
    ;; Calculate the angle, in a 256-degree circle, between two points into A.
    ;; The points (x1, y1) and (x2, y2) have to use *unsigned coordinates only* from the positive quadrant in the carthesian plane!
    ;; http://codebase64.net/doku.php?id=base:8bit_atan2_8-bit_angle
    ;; This uses 2 large lookup tables so uses a lot of memory but is super fast.

    %asm {{

x1 = cx16.r0L
y1 = cx16.r1L
x2 = cx16.r2L
y2 = cx16.r3L
octant = cx16.r4L			;; temporary zeropage variable

		lda x1
		sec
		sbc x2
		bcs *+4
		eor #$ff
		tax
		rol octant

		lda y1
		sec
		sbc y2
		bcs *+4
		eor #$ff
		tay
		rol octant

		lda log2_tab,x
		sec
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
        ; !notreached!
    }}
}

    asmsub diff(ubyte v1 @A, ubyte v2 @Y) -> ubyte @A {
        ; -- returns the (absolute) difference, or distance, between the two bytes
        %asm {{
            sty  P8ZP_SCRATCH_REG
            sec
            sbc  P8ZP_SCRATCH_REG
            bcs  +
            eor  #255
            inc  a
+           rts
        }}
    }

    asmsub diffw(uword w1 @R0, uword w2 @AY) -> uword @AY {
        ; -- returns the (absolute) difference, or distance, between the two words
        %asm {{
            sec
            sbc  cx16.r0L
            sta  cx16.r0L
            tya
            sbc  cx16.r0H
            sta  cx16.r0H
            bcs  +
            eor  #255
            sta  cx16.r0H
            lda  cx16.r0L
            eor  #255
            inc  a
            sta  cx16.r0L
            bne  +
            inc  cx16.r0H
+           lda  cx16.r0L
            ldy  cx16.r0H
            rts
        }}
    }

    sub crc16(^^ubyte data, uword length) -> uword {
        ; calculates the CRC16 (XMODEM) checksum of the buffer.
        ; There are also "streaming" crc16_start/update/end routines below, that allow you to calculate crc16 for data that doesn't fit in a single memory block.
        crc16_start()
        cx16.r13 = data
        cx16.r14 = data+length
        while cx16.r13!=cx16.r14 {
            crc16_update(@(cx16.r13))
            cx16.r13++
        }
        return crc16_end()
    }

    sub crc16_start() {
        ; start the "streaming" crc16
        ; note: tracks the crc16 checksum in cx16.r15!
        ;       if your code uses that, it must save/restore it before calling this routine
        cx16.r15 = 0
    }

    asmsub crc16_update(ubyte value @A) {
        ; update the "streaming" crc16 with next byte value
        ; note: tracks the crc16 checksum in cx16.r15!
        ;       if your code uses that, it must save/restore it before calling this routine
        %asm {{
            eor  cx16.r15H
            sta  cx16.r15H
            ldy  #8
-           asl  cx16.r15L
            rol  cx16.r15H
            bcc  +
            lda  cx16.r15H
            eor  #$10
            sta  cx16.r15H
            lda  cx16.r15L
            eor  #$21
            sta  cx16.r15L
+           dey
            bne  -
            rts
        }}
; orignal prog8 code was:
;        cx16.r15H ^= value
;        repeat 8 {
;            cx16.r15<<=1
;            if_cs
;                cx16.r15 ^= $1021
;        }
    }

    sub crc16_end() -> uword {
        ; finalize the "streaming" crc16, returns resulting crc16 value
        return cx16.r15
    }

    sub crc32(^^ubyte data, uword length) {
        ; Calculates the CRC-32 (ISO-HDLC/PKZIP) checksum of the buffer.
        ; because prog8 doesn't have 32 bits integers, we have to split up the calculation over 2 words.
        ; result stored in cx16.r14 (low word) and cx16.r15 (high word)
        ; There are also "streaming" crc32_start/update/end routines below, that allow you to calculate crc32 for data that doesn't fit in a single memory block.
        crc32_start()
        cx16.r12 = data
        cx16.r13 = data+length
        while cx16.r12!=cx16.r13 {
            crc32_update(@(cx16.r12))
            cx16.r12++
        }
        crc32_end()
    }

    sub crc32_start() {
        ; start the "streaming" crc32
        ; note: tracks the crc32 checksum in cx16.r14 and cx16.r15!
        ;       if your code uses these, it must save/restore them before calling this routine
        cx16.r14 = cx16.r15 = $ffff
    }

    sub crc32_update(ubyte value) {
        ; update the "streaming" crc32 with next byte value
        ; note: tracks the crc32 checksum in cx16.r14 and cx16.r15!
        ;       if your code uses these, it must save/restore them before calling this routine
        ; implementation detail: see https://stackoverflow.com/a/75951866  , the zlib crc32 is the "reflected" variant
        %asm {{
            eor  cx16.r14L
            sta  cx16.r14L
            ldy  #8
-           lsr  cx16.r15H
            ror  cx16.r15L
            ror  cx16.r14H
            ror  cx16.r14L
            bcc  +
            lda  cx16.r15H
            eor  #$ed
            sta  cx16.r15H
            lda  cx16.r15L
            eor  #$b8
            sta  cx16.r15L
            lda  cx16.r14H
            eor  #$83
            sta  cx16.r14H
            lda  cx16.r14L
            eor  #$20
            sta  cx16.r14L
+           dey
            bne  -
            rts
        }}
; original prog8 code:
;        cx16.r14L ^= value
;        repeat 8 {
;            cx16.r15 >>= 1
;            ror(cx16.r14)
;            if_cs {
;                cx16.r15 ^= $edb8
;                cx16.r14 ^= $8320
;            }
;        }
    }

    sub crc32_end() {
        ; finalize the "streaming" crc32
        ; result stored in cx16.r14 (low word) and cx16.r15 (high word)
        void crc32_end_result()
    }

    asmsub crc32_end_result() -> uword @R15, uword @R14 {
        ; finalize the "streaming" crc32
        ; returns the result value in cx16.r15 (high word) and r14 (low word)
        %asm {{
            lda  cx16.r15H
            eor  #255
            sta  cx16.r15H
            lda  cx16.r15L
            eor  #255
            sta  cx16.r15L
            lda  cx16.r14H
            eor  #255
            sta  cx16.r14H
            lda  cx16.r14L
            eor  #255
            sta  cx16.r14L
            rts
        }}
    }


    sub lerp(ubyte v0, ubyte v1, ubyte t) -> ubyte {
        ; Linear interpolation (LERP)
        ; returns an interpolation between two inputs (v0, v1) for a parameter t in the interval [0, 255]
        ; guarantees v = v1 when t = 255
        if v1<v0
            return v0 - msb(t as uword * (v0 - v1) + 255)
        else
            return v0 + msb(t as uword * (v1 - v0) + 255)
    }

    sub lerpw(uword v0, uword v1, uword t) -> uword {
        ; Linear interpolation (LERP) on word values
        ; returns an interpolation between two inputs (v0, v1) for a parameter t in the interval [0, 65535]
        ; guarantees v = v1 when t = 65535.  Clobbers R15.
        if v1<v0 {
            t *= v0-v1
            cx16.r15 = math.mul16_last_upper()
            if t!=0
                cx16.r15++
            return v0 - cx16.r15
        }
        t *= v1-v0
        cx16.r15 = math.mul16_last_upper()
        if t!=0
            cx16.r15++
        return v0 + cx16.r15
    }

    sub interpolate(ubyte v, ubyte inputMin, ubyte inputMax, ubyte outputMin, ubyte outputMax) -> ubyte {
        ; Interpolate a value v in interval [inputMin, inputMax] to output interval [outputMin, outputMax]
        ; Clobbers R15.
        ; (There is no version for words because of lack of precision in the fixed point calculation there)
        cx16.r15 = ((v-inputMin)*256+inputMax) / (inputMax-inputMin)
        cx16.r15 *= (outputMax-outputMin)
        return cx16.r15H + outputMin
    }
}
