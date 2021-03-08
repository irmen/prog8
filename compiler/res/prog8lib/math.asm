; Internal Math library routines - always included by the compiler
; Generic machine independent 6502 code.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
;  some more interesting routines can be found here:
;	http://6502org.wikidot.com/software-math
;	http://codebase64.org/doku.php?id=base:6502_6510_maths
;


math_store_reg	.byte  0		; temporary storage


multiply_bytes	.proc
	; -- multiply 2 bytes A and Y, result as byte in A  (signed or unsigned)
		sta  P8ZP_SCRATCH_B1         ; num1
		sty  P8ZP_SCRATCH_REG        ; num2
		lda  #0
		beq  _enterloop
_doAdd		clc
		adc  P8ZP_SCRATCH_B1
_loop		asl  P8ZP_SCRATCH_B1
_enterloop	lsr  P8ZP_SCRATCH_REG
		bcs  _doAdd
		bne  _loop
		rts
		.pend


multiply_bytes_into_word	.proc
	; -- multiply 2 bytes A and Y, result as word in A/Y (unsigned)
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		stx  math_store_reg
		lda  #0
		ldx  #8
		lsr  P8ZP_SCRATCH_B1
-		bcc  +
		clc
		adc  P8ZP_SCRATCH_REG
+		ror  a
		ror  P8ZP_SCRATCH_B1
		dex
		bne  -
		tay
		lda  P8ZP_SCRATCH_B1
		ldx  math_store_reg
		rts
		.pend


multiply_words	.proc
	; -- multiply two 16-bit words into a 32-bit result  (signed and unsigned)
	;      input: A/Y = first 16-bit number, P8ZP_SCRATCH_W1 in ZP = second 16-bit number
	;      output: multiply_words.result  4-bytes/32-bits product, LSB order (low-to-high)
	;      clobbers: A

		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		stx  P8ZP_SCRATCH_REG

mult16		lda  #0
		sta  result+2	; clear upper bits of product
		sta  result+3
		ldx  #16			; for all 16 bits...
-	 	lsr  P8ZP_SCRATCH_W1+1	; divide multiplier by 2
		ror  P8ZP_SCRATCH_W1
		bcc  +
		lda  result+2	; get upper half of product and add multiplicand
		clc
		adc  P8ZP_SCRATCH_W2
		sta  result+2
		lda  result+3
		adc  P8ZP_SCRATCH_W2+1
+ 		ror  a				; rotate partial product
		sta  result+3
		ror  result+2
		ror  result+1
		ror  result
		dex
		bne  -
		ldx  P8ZP_SCRATCH_REG
		rts

result		.byte  0,0,0,0
		.pend


divmod_b_asm	.proc
	; signed byte division: make everything positive and fix sign afterwards
		sta  P8ZP_SCRATCH_B1
		tya
		eor  P8ZP_SCRATCH_B1
		php			; save sign
		lda  P8ZP_SCRATCH_B1
		bpl  +
		eor  #$ff
		sec
		adc  #0			; make it positive
+		pha
		tya
		bpl  +
		eor  #$ff
		sec
		adc  #0			; make it positive
		tay
+		pla
		jsr  divmod_ub_asm
		sta  _remainder
		plp
		bpl  +
		tya
		eor  #$ff
		sec
		adc  #0			; negate result
		tay
+		rts
_remainder	.byte  0
		.pend


divmod_ub_asm	.proc
	; -- divide A by Y, result quotient in Y, remainder in A   (unsigned)
	;    division by zero will result in quotient = 255 and remainder = original number
		sty  P8ZP_SCRATCH_REG
		sta  P8ZP_SCRATCH_B1
		stx  math_store_reg

		lda  #0
		ldx  #8
		asl  P8ZP_SCRATCH_B1
-		rol  a
		cmp  P8ZP_SCRATCH_REG
		bcc  +
		sbc  P8ZP_SCRATCH_REG
+		rol  P8ZP_SCRATCH_B1
		dex
		bne  -
		ldy  P8ZP_SCRATCH_B1
		ldx  math_store_reg
		rts
		.pend

divmod_w_asm	.proc
	; signed word division: make everything positive and fix sign afterwards
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  P8ZP_SCRATCH_W1+1
		eor  P8ZP_SCRATCH_W2+1
		php			; save sign
		lda  P8ZP_SCRATCH_W1+1
		bpl  +
		lda  #0
		sec
		sbc  P8ZP_SCRATCH_W1
		sta  P8ZP_SCRATCH_W1
		lda  #0
		sbc  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W1+1
+		lda  P8ZP_SCRATCH_W2+1
		bpl  +
		lda  #0
		sec
		sbc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2
		lda  #0
		sbc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W2+1
+		tay
		lda  P8ZP_SCRATCH_W2
		jsr  divmod_uw_asm
		plp			; restore sign
		bpl  +
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  #0
		sec
		sbc  P8ZP_SCRATCH_W2
		pha
		lda  #0
		sbc  P8ZP_SCRATCH_W2+1
		tay
		pla
+		rts
		.pend

divmod_uw_asm	.proc
	; -- divide two unsigned words (16 bit each) into 16 bit results
	;    input:  P8ZP_SCRATCH_W1 in ZP: 16 bit number, A/Y: 16 bit divisor
	;    output: P8ZP_SCRATCH_W2 in ZP: 16 bit remainder, A/Y: 16 bit division result
	;    division by zero will result in quotient = 65535 and remainder = divident


dividend = P8ZP_SCRATCH_W1
remainder = P8ZP_SCRATCH_W2
result = dividend ;save memory by reusing divident to store the result

		sta  _divisor
		sty  _divisor+1
		stx  P8ZP_SCRATCH_REG
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
		ldx  P8ZP_SCRATCH_REG
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


fast_randbyte	.proc
	; -- fast but bad 8-bit pseudo random number generator into A
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

randbyte        .proc
	; -- 8 bit pseudo random number generator into A (by just reusing randword)
		jmp  randword
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


; ----------- optimized multiplications (stack) : ---------
stack_mul_byte_3	.proc
		; X + X*2
		lda  P8ESTACK_LO+1,x
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_3	.proc
		; W*2 + W
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend


stack_mul_byte_5	.proc
		; X*4 + X
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_5	.proc
		; W*4 + W
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend


stack_mul_byte_6	.proc
		; (X*2 + X)*2
		lda  P8ESTACK_LO+1,x
		asl  a
                clc
		adc  P8ESTACK_LO+1,x
		asl  a
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_6	.proc
		; (W*2 + W)*2
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		asl  P8ESTACK_LO+1,x
                rol  a
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_7	.proc
		; X*8 - X
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		sec
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_7	.proc
		; W*8 - W
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		sec
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		sbc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_9	.proc
		; X*8 + X
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_9	.proc
		; W*8 + W
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_10	.proc
		; (X*4 + X)*2
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		asl  a
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_10	.proc
		; (W*4 + W)*2
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		asl  P8ESTACK_LO+1,x
                rol  a
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_11	.proc
		; (X*2 + X)*4 - X
		lda  P8ESTACK_LO+1,x
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		asl  a
		asl  a
		sec
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

; mul_word_11 is skipped (too much code)

stack_mul_byte_12	.proc
		; (X*2 + X)*4
		lda  P8ESTACK_LO+1,x
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		asl  a
		asl  a
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_12	.proc
		; (W*2 + W)*4
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		asl  P8ESTACK_LO+1,x
                rol  a
		asl  P8ESTACK_LO+1,x
                rol  a
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_13	.proc
		; (X*2 + X)*4 + X
		lda  P8ESTACK_LO+1,x
		asl  a
                clc
		adc  P8ESTACK_LO+1,x
		asl  a
		asl  a
                clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

; mul_word_13 is skipped (too much code)

stack_mul_byte_14	.proc
		; (X*8 - X)*2
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
                sec
		sbc  P8ESTACK_LO+1,x
                asl  a
		sta  P8ESTACK_LO+1,x
		rts
		.pend

; mul_word_14 is skipped (too much code)

stack_mul_byte_15	.proc
		; X*16 - X
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		asl  a
		sec
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_15	.proc
		; W*16 - W
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		sec
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		sbc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_20	.proc
		; (X*4 + X)*4
		lda  P8ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		asl  a
		asl  a
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_20	.proc
		; (W*4 + W)*4
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		asl  P8ESTACK_LO+1,x
                rol  a
		asl  P8ESTACK_LO+1,x
                rol  a
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_25	.proc
		; (X*2 + X)*8 + X
		lda  P8ESTACK_LO+1,x
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_25	.proc
		; W = (W*2 + W) *8 + W
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ZP_SCRATCH_W1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_40	.proc
		lda  P8ESTACK_LO+1,x
		and  #7
		tay
		lda  mul_byte_40._forties,y
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_40	.proc
		; (W*4 + W)*8
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+1,x
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_REG
		adc  P8ESTACK_HI+1,x
		asl  P8ESTACK_LO+1,x
                rol  a
		asl  P8ESTACK_LO+1,x
                rol  a
		asl  P8ESTACK_LO+1,x
                rol  a
		sta  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_50	.proc
		lda  P8ESTACK_LO+1,x
		and  #7
		tay
		lda  mul_byte_50._fifties, y
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_50	.proc
		; W = W * 25 * 2
		jsr  stack_mul_word_25
		asl  P8ESTACK_LO+1,x
		rol  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_80	.proc
		lda  P8ESTACK_LO+1,x
		and  #3
		tay
		lda  mul_byte_80._eighties, y
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_80	.proc
		; W = W * 40 * 2
		jsr  stack_mul_word_40
		asl  P8ESTACK_LO+1,x
		rol  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_byte_100	.proc
		lda  P8ESTACK_LO+1,x
		and  #3
		tay
		lda  mul_byte_100._hundreds, y
		sta  P8ESTACK_LO+1,x
		rts
		.pend

stack_mul_word_100	.proc
		; W = W * 25 * 4
		jsr  stack_mul_word_25
		asl  P8ESTACK_LO+1,x
		rol  P8ESTACK_HI+1,x
		asl  P8ESTACK_LO+1,x
		rol  P8ESTACK_HI+1,x
		rts
		.pend

stack_mul_word_320	.proc
		; stackW = stackLo * 256 + stackLo * 64	 (stackHi doesn't matter)
		ldy  P8ESTACK_LO+1,x
		lda  #0
		sta  P8ESTACK_HI+1,x
		tya
		asl  a
		rol  P8ESTACK_HI+1,x
		asl  a
		rol  P8ESTACK_HI+1,x
		asl  a
		rol  P8ESTACK_HI+1,x
		asl  a
		rol  P8ESTACK_HI+1,x
		asl  a
		rol  P8ESTACK_HI+1,x
		asl  a
		rol  P8ESTACK_HI+1,x
		sta  P8ESTACK_LO+1,x
		tya
		clc
		adc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

; ----------- optimized multiplications (in-place A (byte) and ?? (word)) : ---------
mul_byte_3	.proc
		; A = A + A*2
		sta  P8ZP_SCRATCH_REG
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_3	.proc
		; AY = AY*2 + AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		.pend


mul_byte_5	.proc
		; A = A*4 + A
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_5	.proc
		; AY = AY*4 + AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		.pend


mul_byte_6	.proc
		; A = (A*2 + A)*2
		sta  P8ZP_SCRATCH_REG
		asl  a
                clc
                adc  P8ZP_SCRATCH_REG
		asl  a
		rts
		.pend

mul_word_6	.proc
		; AY = (AY*2 + AY)*2
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		tay
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W1+1
		tya
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		ldy  P8ZP_SCRATCH_W1+1
		rts
		.pend

mul_byte_7	.proc
		; A = A*8 - A
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		asl  a
		sec
		sbc  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_7	.proc
		; AY = AY*8 - AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		sec
		sbc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		sbc  P8ZP_SCRATCH_W2+1
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		.pend

mul_byte_9	.proc
		; A = A*8 + A
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_9	.proc
		; AY = AY*8 + AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		rts
		.pend

mul_byte_10	.proc
		; A=(A*4 + A)*2
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		asl  a
		rts
		.pend

mul_word_10	.proc
		; AY=(AY*4 + AY)*2
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ZP_SCRATCH_W1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		ldy  P8ZP_SCRATCH_W1+1
		rts
		.pend

mul_byte_11	.proc
		; A=(A*2 + A)*4 - A
		sta  P8ZP_SCRATCH_REG
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		sec
		sbc  P8ZP_SCRATCH_REG
		rts
		.pend

; mul_word_11 is skipped (too much code)

mul_byte_12	.proc
		; A=(A*2 + A)*4
		sta  P8ZP_SCRATCH_REG
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		rts
		.pend

mul_word_12	.proc
		; AY=(AY*2 + AY)*4
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ZP_SCRATCH_W1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		ldy  P8ZP_SCRATCH_W1+1
		rts
		.pend

mul_byte_13	.proc
		; A=(A*2 + A)*4 + A
		sta  P8ZP_SCRATCH_REG
		asl  a
                clc
		adc  P8ZP_SCRATCH_REG
		asl  a
		asl  a
                clc
		adc  P8ZP_SCRATCH_REG
		rts
		.pend

; mul_word_13 is skipped (too much code)

mul_byte_14	.proc
		; A=(A*8 - A)*2
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		asl  a
                sec
		sbc  P8ZP_SCRATCH_REG
                asl  a
		rts
		.pend

; mul_word_14 is skipped (too much code)

mul_byte_15	.proc
		; A=A*16 - A
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		asl  a
		asl  a
		sec
		sbc  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_15	.proc
		; AY = AY * 16 - AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		sec
		sbc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		sbc  P8ZP_SCRATCH_W2+1
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		.pend

mul_byte_20	.proc
		; A=(A*4 + A)*4
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		rts
		.pend

mul_word_20	.proc
		; AY = AY * 10 * 2
		jsr  mul_word_10
		sty  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		ldy  P8ZP_SCRATCH_REG
		rts
		.pend

mul_byte_25	.proc
		; A=(A*2 + A)*8 + A
		sta  P8ZP_SCRATCH_REG
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_25	.proc
		; AY = (AY*2 + AY) *8 + AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ZP_SCRATCH_W1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		.pend

mul_byte_40	.proc
		and  #7
		tay
		lda  _forties,y
		rts
_forties	.byte  0*40, 1*40, 2*40, 3*40, 4*40, 5*40, 6*40, 7*40 & 255
		.pend

mul_word_40	.proc
		; AY = (AY*4 + AY)*8
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		asl  a
		rol  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		adc  P8ZP_SCRATCH_W2+1
		asl  P8ZP_SCRATCH_W1
		rol  a
		asl  P8ZP_SCRATCH_W1
		rol  a
		asl  P8ZP_SCRATCH_W1
		rol  a
		tay
		lda  P8ZP_SCRATCH_W1
		rts
		.pend

mul_byte_50	.proc
		and  #7
		tay
		lda  _fifties, y
		rts
_fifties	.byte  0*50, 1*50, 2*50, 3*50, 4*50, 5*50, 6*50 & 255, 7*50 & 255
		.pend

mul_word_50	.proc
		; AY = AY * 25 * 2
		jsr  mul_word_25
		sty  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		ldy  P8ZP_SCRATCH_REG
		rts
		.pend

mul_byte_80	.proc
		and  #3
		tay
		lda  _eighties, y
		rts
_eighties	.byte  0*80, 1*80, 2*80, 3*80
		.pend

mul_word_80	.proc
		; AY = AY * 40 * 2
		jsr  mul_word_40
		sty  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		ldy  P8ZP_SCRATCH_REG
		rts
		.pend

mul_byte_100	.proc
		and  #3
		tay
		lda  _hundreds, y
		rts
_hundreds	.byte  0*100, 1*100, 2*100, 3*100 & 255
		.pend

mul_word_100	.proc
		; AY = AY * 25 * 4
		jsr  mul_word_25
		sty  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		ldy  P8ZP_SCRATCH_REG
		rts
		.pend

mul_word_320	.proc
		; AY = A * 256 + A * 64	 (msb in Y doesn't matter)
		sta  P8ZP_SCRATCH_B1
		ldy  #0
		sty  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		pha
		clc
		lda  P8ZP_SCRATCH_B1
		adc  P8ZP_SCRATCH_REG
		tay
		pla
		rts
		.pend

; ----------- end optimized multiplications -----------


; bit shifts.
; anything below 3 is done inline. anything above 7 is done via other optimizations.

shift_left_w_7	.proc
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x

		asl  a
		rol  P8ZP_SCRATCH_B1
_shift6		asl  a
		rol  P8ZP_SCRATCH_B1
_shift5		asl  a
		rol  P8ZP_SCRATCH_B1
_shift4		asl  a
		rol  P8ZP_SCRATCH_B1
_shift3		asl  a
		rol  P8ZP_SCRATCH_B1
		asl  a
		rol  P8ZP_SCRATCH_B1
		asl  a
		rol  P8ZP_SCRATCH_B1

		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_B1
		sta  P8ESTACK_HI+1,x
		rts
		.pend

shift_left_w_6	.proc
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		jmp  shift_left_w_7._shift6
		.pend

shift_left_w_5	.proc
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		jmp  shift_left_w_7._shift5
		.pend

shift_left_w_4	.proc
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		jmp  shift_left_w_7._shift4
		.pend

shift_left_w_3	.proc
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		jmp  shift_left_w_7._shift3
		.pend


shift_left_w	.proc
		; -- variable number of shifts left
		inx
		ldy  P8ESTACK_LO,x
		bne  _shift
		rts
_shift		asl  P8ESTACK_LO+1,x
		rol  P8ESTACK_HI+1,x
		dey
		bne  _shift
		rts
		.pend

shift_right_uw	.proc
		; -- uword variable number of shifts right
		inx
		ldy  P8ESTACK_LO,x
		bne  _shift
		rts
_shift		lsr  P8ESTACK_HI+1,x
		ror  P8ESTACK_LO+1,x
		dey
		bne  _shift
		rts
		.pend

shift_right_uw_7	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_HI+1,x

		lsr  a
		ror  P8ZP_SCRATCH_B1
_shift6		lsr  a
		ror  P8ZP_SCRATCH_B1
_shift5		lsr  a
		ror  P8ZP_SCRATCH_B1
_shift4		lsr  a
		ror  P8ZP_SCRATCH_B1
_shift3		lsr  a
		ror  P8ZP_SCRATCH_B1
		lsr  a
		ror  P8ZP_SCRATCH_B1
		lsr  a
		ror  P8ZP_SCRATCH_B1

		sta  P8ESTACK_HI+1,x
		lda  P8ZP_SCRATCH_B1
		sta  P8ESTACK_LO+1,x
		rts
		.pend

shift_right_uw_6	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift6
		.pend

shift_right_uw_5	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift5
		.pend

shift_right_uw_4	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift4
		.pend

shift_right_uw_3	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_HI+1,x
		jmp  shift_right_uw_7._shift3
		.pend


shift_right_w_7		.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1

		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1

		lda  P8ZP_SCRATCH_W1+1
_shift6		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
_shift5		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
_shift4		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
_shift3		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		asl  a
		ror  P8ZP_SCRATCH_W1+1
		ror  P8ZP_SCRATCH_W1

		lda  P8ZP_SCRATCH_W1
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_W1+1
		sta  P8ESTACK_HI+1,x
		rts
		.pend

shift_right_w_6	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		jmp  shift_right_w_7._shift6
		.pend

shift_right_w_5	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		jmp  shift_right_w_7._shift5
		.pend

shift_right_w_4	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		jmp  shift_right_w_7._shift4
		.pend

shift_right_w_3	.proc
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		jmp  shift_right_w_7._shift3
		.pend


shift_right_w	.proc
		; -- signed word variable number of shifts right
		inx
		ldy  P8ESTACK_LO,x
		bne  _shift
		rts
_shift		lda  P8ESTACK_HI+1,x
		asl  a
		ror  P8ESTACK_HI+1,x
		ror  P8ESTACK_LO+1,x
		dey
		bne  _shift
		rts
		.pend


; support for bit shifting that is too large to be unrolled:

lsr_byte_A	.proc
		; -- lsr signed byte in A times the value in Y (assume >0)
		cmp  #0
		bmi  _negative
-		lsr  a
		dey
		bne  -
		rts
_negative	lsr  a
		ora  #$80
		dey
		bne  _negative
		rts
		.pend


square          .proc
; -- calculate square root of signed word in AY, result in AY
; routine by Lee Davsion, source: http://6502.org/source/integers/square.htm
; using this routine is about twice as fast as doing a regular multiplication.
;
; Calculates the 16 bit unsigned integer square of the signed 16 bit integer in
; Numberl/Numberh.  The result is always in the range 0 to 65025 and is held in
; Squarel/Squareh
;
; The maximum input range is only +/-255 and no checking is done to ensure that
; this is so.
;
; This routine is useful if you are trying to draw circles as for any circle
;
; x^2+y^2=r^2 where x and y are the co-ordinates of any point on the circle and
; r is the circle radius

numberl = P8ZP_SCRATCH_W1       ; number to square low byte
numberh = P8ZP_SCRATCH_W1+1     ; number to square high byte
squarel = P8ZP_SCRATCH_W2       ; square low byte
squareh = P8ZP_SCRATCH_W2+1     ; square high byte
tempsq = P8ZP_SCRATCH_B1        ; temp byte for intermediate result

	sta  numberl
	sty  numberh
	stx  P8ZP_SCRATCH_REG

        lda     #$00        ; clear a
        sta     squarel     ; clear square low byte
                            ; (no need to clear the high byte, it gets shifted out)
        lda	numberl     ; get number low byte
	ldx	numberh     ; get number high  byte
	bpl	_nonneg      ; if +ve don't negate it
                            ; else do a two's complement
	eor	#$ff        ; invert
        sec	            ; +1
	adc	#$00        ; and add it

_nonneg:
	sta	tempsq      ; save abs(number)
	ldx	#$08        ; set bit count

_nextr2bit:
	asl	squarel     ; low byte *2
	rol	squareh     ; high byte *2+carry from low
	asl	a           ; shift number byte
	bcc	_nosqadd     ; don't do add if c = 0
	tay                 ; save a
	clc                 ; clear carry for add
	lda	tempsq      ; get number
	adc	squarel     ; add number^2 low byte
	sta	squarel     ; save number^2 low byte
	lda	#$00        ; clear a
	adc	squareh     ; add number^2 high byte
	sta	squareh     ; save number^2 high byte
	tya                 ; get a back

_nosqadd:
	dex                 ; decrement bit count
	bne	_nextr2bit   ; go do next bit

	lda  squarel
	ldy  squareh
	ldx  P8ZP_SCRATCH_REG
	rts

		.pend
