; Prog8 internal Math library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


;  some more interesting routines can be found here:
;	http://6502org.wikidot.com/software-math
;	http://codebase64.org/doku.php?id=base:6502_6510_maths
;

multiply_bytes	.proc
	; -- multiply 2 bytes A and Y, result as byte in A  (signed or unsigned)
		sta  c64.SCRATCH_ZPB1         ; num1
		sty  c64.SCRATCH_ZPREG        ; num2
		lda  #0
		beq  _enterloop
_doAdd		clc
		adc  c64.SCRATCH_ZPB1
_loop		asl  c64.SCRATCH_ZPB1
_enterloop	lsr  c64.SCRATCH_ZPREG
		bcs  _doAdd
		bne  _loop
		rts
		.pend


multiply_bytes_16	.proc
	; -- multiply 2 bytes A and Y, result as word in A/Y (unsigned)
		sta  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		stx  c64.SCRATCH_ZPREGX
		lda  #0
		ldx  #8
		lsr  c64.SCRATCH_ZPB1
-		bcc  +
		clc
		adc  c64.SCRATCH_ZPREG
+		ror  a
		ror  c64.SCRATCH_ZPB1
		dex
		bne  -
		tay
		lda  c64.SCRATCH_ZPB1
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend


multiply_words	.proc
	; -- multiply two 16-bit words into a 32-bit result  (signed and unsigned)
	;      input: A/Y = first 16-bit number, c64.SCRATCH_ZPWORD1 in ZP = second 16-bit number
	;      output: multiply_words.result  4-bytes/32-bits product, LSB order (low-to-high)
	;      clobbers: A

		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		stx  c64.SCRATCH_ZPREGX

mult16		lda  #$00
		sta  result+2	; clear upper bits of product
		sta  result+3
		ldx  #16			; for all 16 bits...
-	 	lsr  c64.SCRATCH_ZPWORD1+1	; divide multiplier by 2
		ror  c64.SCRATCH_ZPWORD1
		bcc  +
		lda  result+2	; get upper half of product and add multiplicand
		clc
		adc  c64.SCRATCH_ZPWORD2
		sta  result+2
		lda  result+3
		adc  c64.SCRATCH_ZPWORD2+1
+ 		ror  a				; rotate partial product
		sta  result+3
		ror  result+2
		ror  result+1
		ror  result
		dex
		bne  -
		ldx  c64.SCRATCH_ZPREGX
		rts

result		.byte  0,0,0,0
		.pend


divmod_ub	.proc
	; -- divide A by Y, result quotient in Y, remainder in A   (unsigned)
	;    division by zero will result in quotient = 255 and remainder = original number
		sty  c64.SCRATCH_ZPREG
		sta  c64.SCRATCH_ZPB1
		stx  c64.SCRATCH_ZPREGX

		lda  #0
		ldx  #8
		asl  c64.SCRATCH_ZPB1
-		rol  a
		cmp  c64.SCRATCH_ZPREG
		bcc  +
		sbc  c64.SCRATCH_ZPREG
+		rol  c64.SCRATCH_ZPB1
		dex
		bne  -
		ldy  c64.SCRATCH_ZPB1
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend

divmod_uw_asm	.proc
	; -- divide two unsigned words (16 bit each) into 16 bit results
	;    input:  c64.SCRATCH_ZPWORD1 in ZP: 16 bit number, A/Y: 16 bit divisor
	;    output: c64.SCRATCH_ZPWORD2 in ZP: 16 bit remainder, A/Y: 16 bit division result
	;    division by zero will result in quotient = 65535 and remainder = divident


dividend = c64.SCRATCH_ZPWORD1
remainder = c64.SCRATCH_ZPWORD2
result = dividend ;save memory by reusing divident to store the result

		sta  _divisor
		sty  _divisor+1
		stx  c64.SCRATCH_ZPREGX
		lda  #0	        	;preset remainder to 0
		sta  remainder
		sta  remainder+1
		ldx  #16	        ;repeat for each bit: ...

-		asl  dividend		;dividend lb & hb*2, msb -> Carry
		rol  dividend+1
		rol  remainder		;remainder lb & hb * 2 + msb from carry
		rol  remainder+1
		lda  remainder
		sec
		sbc  _divisor		;substract divisor to see if it fits in
		tay	       		;lb result -> Y, for we may need it later
		lda  remainder+1
		sbc  _divisor+1
		bcc  +			;if carry=0 then divisor didn't fit in yet

		sta  remainder+1	;else save substraction result as new remainder,
		sty  remainder
		inc  result		;and INCrement result cause divisor fit in 1 times

+		dex
		bne  -

		lda  result
		ldy  result+1
		ldx  c64.SCRATCH_ZPREGX
		rts
_divisor	.word 0
		.pend


randseed	.proc
	; -- reset the random seeds for the byte and word random generators
	;    arguments: uword seed in A/Y   clobbers A
	;    (default starting values are:  A=$2c Y=$9e)
		sta  randword._seed
		sty  randword._seed+1
		clc
		adc  #14
		sta  randbyte._seed
		rts
		.pend


randbyte	.proc
	; -- 8-bit pseudo random number generator into A
		lda  _seed
		beq  _eor
		asl  a
		beq  _done	; if the input was $80, skip the EOR
		bcc  _done
_eor		eor  #$1d	; xor with magic value see below for possible values
_done		sta  _seed
		rts

_seed		.byte  $3a

		; possible 'magic' eor bytes are:
		; $1d, $2b, $2d, $4d, $5f, $63, $65, $69
		; $71, $87, $8d, $a9, $c3, $cf, $e7, $f5

		.pend


randword	.proc
	; -- 16 bit pseudo random number generator into AY

magic_eor = $3f1d
		; possible magic eor words are:
		; $3f1d, $3f81, $3fa5, $3fc5, $4075, $409d, $40cd, $4109
 		; $413f, $414b, $4153, $4159, $4193, $4199, $41af, $41bb

		lda  _seed
		beq  _lowZero	; $0000 and $8000 are special values to test for

 		; Do a normal shift
		asl  _seed
		lda  _seed+1
		rol  a
		bcc  _noEor

_doEor		; high byte is in A
		eor  #>magic_eor
  		sta  _seed+1
  		lda  _seed
  		eor  #<magic_eor
  		sta  _seed
  		ldy  _seed+1
  		rts

_lowZero	lda  _seed+1
		beq  _doEor	; High byte is also zero, so apply the EOR
				; For speed, you could store 'magic' into 'seed' directly
				; instead of running the EORs

 		; wasn't zero, check for $8000
		asl  a
		beq  _noEor	; if $00 is left after the shift, then it was $80
		bcs  _doEor	; else, do the EOR based on the carry bit as usual

_noEor		sta  _seed+1
		tay
		lda  _seed
 		rts

_seed		.word	$2c9e
		.pend


mul_byte_3	.proc
		; X + X*2
		lda  c64.ESTACK_LO+1,x
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_3	.proc
		; W*2 + W
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend


mul_byte_5	.proc
		; X*4 + X
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_5	.proc
		; W*4 + W
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend


mul_byte_6	.proc
		; (X*2 + X)*2
		lda  c64.ESTACK_LO+1,x
		asl  a
                clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_6	.proc
		; (W*2 + W)*2
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		asl  c64.ESTACK_LO+1,x
                rol  a
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_7	.proc
		; X*8 - X
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		sec
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_7	.proc
		; W*8 - W
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		sec
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		sbc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_9	.proc
		; X*8 + X
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_9	.proc
		; W*8 + W
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_10	.proc
		; (X*4 + X)*2
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_10	.proc
		; (W*4 + W)*2
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		asl  c64.ESTACK_LO+1,x
                rol  a
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_11	.proc
		; (X*2 + X)*4 - X
		lda  c64.ESTACK_LO+1,x
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		sec
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

; mul_word_11 is skipped (too much code)

mul_byte_12	.proc
		; (X*2 + X)*4
		lda  c64.ESTACK_LO+1,x
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_12	.proc
		; (W*2 + W)*4
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		asl  c64.ESTACK_LO+1,x
                rol  a
		asl  c64.ESTACK_LO+1,x
                rol  a
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_13	.proc
		; (X*2 + X)*4 + X
		lda  c64.ESTACK_LO+1,x
		asl  a
                clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		asl  a
                clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

; mul_word_13 is skipped (too much code)

mul_byte_14	.proc
		; (X*8 - X)*2
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
                sec
		sbc  c64.ESTACK_LO+1,x
                asl  a
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

; mul_word_14 is skipped (too much code)

mul_byte_15	.proc
		; X*16 - X
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		asl  a
		sec
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_15	.proc
		; W*16 - W
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		sec
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		sbc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_20	.proc
		; (X*4 + X)*4
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_20	.proc
		; (W*4 + W)*4
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		asl  c64.ESTACK_LO+1,x
                rol  a
		asl  c64.ESTACK_LO+1,x
                rol  a
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_25	.proc
		; (X*2 + X)*8 + X
		lda  c64.ESTACK_LO+1,x
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_25	.proc
		; W + W*8 + W*16
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPWORD1+1
		asl  a
		rol  c64.SCRATCH_ZPWORD1+1
		asl  a
		rol  c64.SCRATCH_ZPWORD1+1
		sta  c64.SCRATCH_ZPWORD1
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		adc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		lda  c64.SCRATCH_ZPWORD1
		asl  a
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		adc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_40	.proc
		; (X*4 + X)*8
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
                clc
		adc  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_40	.proc
		; (W*4 + W)*8
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPREG
		lda  c64.ESTACK_LO+1,x
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPREG
		adc  c64.ESTACK_HI+1,x
		asl  c64.ESTACK_LO+1,x
                rol  a
		asl  c64.ESTACK_LO+1,x
                rol  a
		asl  c64.ESTACK_LO+1,x
                rol  a
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

sign_b		.proc
		lda  c64.ESTACK_LO+1,x
		beq  _sign_zero
		bmi  _sign_neg
_sign_pos	lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
_sign_neg	lda  #-1
_sign_zero	sta  c64.ESTACK_LO+1,x
		rts
		.pend

sign_ub		.proc
		lda  c64.ESTACK_LO+1,x
		beq  sign_b._sign_zero
		bne  sign_b._sign_pos
		.pend

sign_w		.proc
		lda  c64.ESTACK_HI+1,x
		bmi  sign_b._sign_neg
		beq  sign_ub
		bne  sign_b._sign_pos
		.pend

sign_uw		.proc
		lda  c64.ESTACK_HI+1,x
		beq  _sign_possibly_zero
_sign_pos	lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
_sign_possibly_zero	lda  c64.ESTACK_LO+1,x
		bne  _sign_pos
		sta  c64.ESTACK_LO+1,x
		rts
		.pend



; bit shifts.
; anything below 3 is done inline. anything above 7 is done via other optimizations.

shift_left_w_7	.proc
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_LO+1,x

		asl  a
		rol  c64.SCRATCH_ZPB1
_shift6		asl  a
		rol  c64.SCRATCH_ZPB1
_shift5		asl  a
		rol  c64.SCRATCH_ZPB1
_shift4		asl  a
		rol  c64.SCRATCH_ZPB1
_shift3		asl  a
		rol  c64.SCRATCH_ZPB1
		asl  a
		rol  c64.SCRATCH_ZPB1
		asl  a
		rol  c64.SCRATCH_ZPB1

		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPB1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

shift_left_w_6	.proc
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_LO+1,x
		jmp  shift_left_w_7._shift6
		.pend

shift_left_w_5	.proc
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_LO+1,x
		jmp  shift_left_w_7._shift5
		.pend

shift_left_w_4	.proc
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_LO+1,x
		jmp  shift_left_w_7._shift4
		.pend

shift_left_w_3	.proc
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_LO+1,x
		jmp  shift_left_w_7._shift3
		.pend

shift_right_uw_7	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_HI+1,x

		lsr  a
		ror  c64.SCRATCH_ZPB1
_shift6		lsr  a
		ror  c64.SCRATCH_ZPB1
_shift5		lsr  a
		ror  c64.SCRATCH_ZPB1
_shift4		lsr  a
		ror  c64.SCRATCH_ZPB1
_shift3		lsr  a
		ror  c64.SCRATCH_ZPB1
		lsr  a
		ror  c64.SCRATCH_ZPB1
		lsr  a
		ror  c64.SCRATCH_ZPB1

		sta  c64.ESTACK_HI+1,x
		lda  c64.SCRATCH_ZPB1
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

shift_right_uw_6	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift6
		.pend

shift_right_uw_5	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift5
		.pend

shift_right_uw_4	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift4
		.pend

shift_right_uw_3	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPB1
		lda  c64.ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift3
		.pend


shift_right_w_7		.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1

		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1

		lda  c64.SCRATCH_ZPWORD1+1
_shift6		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
_shift5		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
_shift4		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
_shift3		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
		asl  a
		ror  c64.SCRATCH_ZPWORD1+1
		ror  c64.SCRATCH_ZPWORD1

		lda  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

shift_right_w_6	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		jmp  shift_right_w_7._shift6
		.pend

shift_right_w_5	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		jmp  shift_right_w_7._shift5
		.pend

shift_right_w_4	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		jmp  shift_right_w_7._shift4
		.pend

shift_right_w_3	.proc
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		jmp  shift_right_w_7._shift3
		.pend

