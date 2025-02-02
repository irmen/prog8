; Internal Math library routines - always included by the compiler
; Generic machine independent 6502 code.
;
;  some more interesting routines can be found here:
;	http://6502org.wikidot.com/software-math
;	http://codebase64.org/doku.php?id=base:6502_6510_maths
;       https://github.com/TobyLobster/multiply_test
;       https://github.com/TobyLobster/sqrt_test


multiply_bytes	.proc
	; -- multiply 2 bytes A and Y, result as byte in A  (signed or unsigned)
	; https://github.com/TobyLobster/multiply_test/blob/main/tests/mult29.a

_multiplicand    = P8ZP_SCRATCH_B1
_multiplier      = P8ZP_SCRATCH_REG

    sty  _multiplicand
    lsr  a
    sta  _multiplier
    lda  #0
    ldx  #2
-
    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier
    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier

    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier
    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier
    dex
    bne  -
    ; tay       ; if you want 16 bits result in AY, enable this again
    lda  _multiplier
    rts
		.pend


multiply_words	.proc
	; -- multiply two 16-bit words into a 32-bit result  (UNSIGNED)
	;      input: A/Y = first 16-bit number, multiply_words.multiplier = second 16-bit number
	;      output: multiply_words.result, 4-bytes/32-bits product, LSB order (low-to-high)  low 16 bits also in AY.
	;      you can retrieve the upper 16 bits via math.mul16_last_upper()

	; NOTE FOR NEGATIVE VALUES:
	;      The routine also works for NEGATIVE (signed) word values, but ONLY the lower 16 bits of the result are correct then!
	;      Prog8 only uses those so that's not an issue, but math.mul16_last_upper() no longer gives the correct result here.

; mult62.a
; from: https://github.com/TobyLobster/multiply_test/blob/main/tests/mult62.a
; based on Dr Jefyll, http://forum.6502.org/viewtopic.php?f=9&t=689&start=0#p19958
; - adjusted to use fixed zero page addresses
; - removed 'decrement to avoid clc' as this is slower on average
; - rearranged memory use to remove final memory copy and give LSB first order to result
; - removed temp zp storage bytes
; - unrolled the outer loop
; - unrolled the two inner loops once
;
; 16 bit x 16 bit unsigned multiply, 32 bit result
; Average cycles: ~442 ?
; 93 bytes

_multiplicand    = P8ZP_SCRATCH_W2   ; 2 bytes
multiplier      = result

; 16 bit x 16 bit unsigned multiply, 32 bit result
;
; On Entry:
;   (multiplier, multiplier+1): two byte multiplier, four bytes needed for result
;   (multiplicand, multiplicand+1): two byte multiplicand
; On Exit:
;   (result, result+1, result+2, result+3): product

    sta  _multiplicand
    sty  _multiplicand+1

    lda  #0              ;
    sta  result+2        ; 16 bits of zero in A, result+2
                        ;  Note:    First 8 shifts are  A -> result+2 -> result
                        ;           Final 8 shifts are  A -> result+2 -> result+1

    ; --- 1st byte ---
    ldy  #4              ; count for inner loop
    lsr  result

    ; inner loop (8 times)
_inner_loop
    ; first time
    bcc +
    tax                 ; retain A
    lda  result+2
    clc
    adc  _multiplicand
    sta  result+2
    txa                 ; recall A
    adc  _multiplicand+1

+
    ror  a                ; shift
    ror  result+2
    ror  result

    ; second time
    bcc +
    tax                 ; retain A
    lda  result+2
    clc
    adc  _multiplicand
    sta  result+2
    txa                 ; recall A
    adc  _multiplicand+1

+
    ror  a                 ; shift
    ror  result+2
    ror  result

    dey
    bne  _inner_loop      ; go back for 1 more shift?

    ; --- 2nd byte ---
    ldy  #4              ; count for inner loop
    lsr  result+1

    ; inner loop (8 times)
_inner_loop2
    ; first time
    bcc  +
    tax                 ; retain A
    lda  result+2
    clc
    adc  _multiplicand
    sta  result+2
    txa                 ; recall A
    adc  _multiplicand+1

+
    ror  a                ; shift
    ror  result+2
    ror  result+1

    ; second time
    bcc  +
    tax                 ; retain A
    lda  result+2
    clc
    adc  _multiplicand
    sta  result+2
    txa                 ; recall A
    adc  _multiplicand+1

+
    ror  a                ; shift
    ror  result+2
    ror  result+1
    dey
    bne  _inner_loop2     ; go back for 1 more shift?

    sta  result+3        ; ms byte of hi-word of result

    lda  result
    ldy  result+1
    rts

		.section BSS
result		.byte  ?,?,?,?       ; routine could be faster if this were in Zeropage...
		.send BSS
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
		.section BSS
_remainder	.byte  ?
		.send BSS
		.pend


divmod_ub_asm	.proc
	; -- divide A by Y, result quotient in Y, remainder in A   (unsigned)
	;    division by zero will result in quotient = 255 and remainder = original number
		sty  P8ZP_SCRATCH_REG
		sta  P8ZP_SCRATCH_B1

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
		rts
		.section BSS
_divisor	.word ?
		.send BSS
		.pend

; TODO: Romable (find a way to init these variables. Maybe allocate them in slabs_BSS to make it even more random?)
randword	.proc
	; -- 16 bit pseudo random number generator into AY
	;    default seed = $00c2 $1137
        ;    routine from https://codebase64.org/doku.php?id=base:x_abc_random_number_generator_8_16_bit
		inc x1
		clc
x1=*+1
		lda #$00	;x1
c1=*+1
		eor #$c2	;c1
a1=*+1
		eor #$11	;a1
		sta a1
b1=*+1
		adc #$37	;b1
		sta b1
		lsr a
		eor a1
		adc c1
		sta c1
		ldy b1
		rts
		.pend

randbyte = randword    ; -- 8 bit pseudo random number generator into A (by just reusing randword)


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

mul_word_640	.proc
		; AY = (A * 2 * 320) (msb in Y doesn't matter)
		asl  a
		jmp  mul_word_320
		.pend


; ----------- end optimized multiplications -----------


; support for bit shifting that is too large to be unrolled:

lsr_byte_A	.proc
		; -- lsr signed byte in A times the value in Y
		cpy  #0
		beq  +
		cmp  #0
		bpl  lsr_ubyte_A
-       	sec
		ror  a
		dey
		bne  -
+		rts
		.pend

lsr_ubyte_A	.proc
		; -- lsr unsigned byte in A times the value in Y
		cpy  #0
		beq  +
-		lsr  a
		dey
		bne  -
+		rts
		.pend

asl_byte_A      .proc
		; -- asl any byte in A times the value in Y
		cpy  #0
		beq  +
-		asl  a
		dey
		bne  -
+		rts
		.pend


lsr_word_AY     .proc
		; -- lsr signed word in AY times the value in X
		cpx  #0
		beq  +
		cpy  #0
		bpl  lsr_uword_AY
		sty  P8ZP_SCRATCH_B1
-		sec
		ror  P8ZP_SCRATCH_B1
		ror  a
		dex
		bne  -
		ldy  P8ZP_SCRATCH_B1
+		rts
		.pend

lsr_uword_AY    .proc
		; -- lsr unsigned word in AY times the value in X
		cpx  #0
		beq  +
		sty  P8ZP_SCRATCH_B1
-		lsr  P8ZP_SCRATCH_B1
		ror  a
		dex
		bne  -
		ldy  P8ZP_SCRATCH_B1
+		rts
		.pend

asl_word_AY     .proc
		; -- asl any word in AY times the value in X
		cpx  #0
		beq  +
		sty  P8ZP_SCRATCH_B1
-               asl  a
		rol  P8ZP_SCRATCH_B1
		dex
		bne  -
		ldy  P8ZP_SCRATCH_B1
+		rts
		.pend


square          .proc
; -- calculate square of signed word (actually -255..255) in AY, result in AY
; routine by Lee Davison, source: http://6502.org/source/integers/square.htm
; using this routine is a lot faster as doing a regular multiplication (for words)
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
	rts

		.pend
