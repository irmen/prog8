; Prog8 internal library routines - always included by the compiler
; Generic machine independent 6502 code.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


read_byte_from_address_on_stack	.proc
	; -- read the byte from the memory address on the top of the stack, return in A (stack remains unchanged)
		lda  P8ESTACK_LO+1,x
		ldy  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W2),y
		rts
		.pend


write_byte_to_address_on_stack	.proc
	; -- write the byte in A to the memory address on the top of the stack (stack remains unchanged)
		ldy  P8ESTACK_LO+1,x
		sty  P8ZP_SCRATCH_W2
		ldy  P8ESTACK_HI+1,x
		sty  P8ZP_SCRATCH_W2+1
		ldy  #0
		sta  (P8ZP_SCRATCH_W2),y
		rts
		.pend


add_a_to_zpword	.proc
	; -- add ubyte in A to the uword in P8ZP_SCRATCH_W1
		clc
		adc  P8ZP_SCRATCH_W1
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		rts
		.pend

pop_index_times_5	.proc
		inx
		lda  P8ESTACK_LO,x
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO,x
		rts
		.pend

neg_b		.proc
		lda  #0
		sec
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		rts
		.pend

neg_w		.proc
		sec
		lda  #0
		sbc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  #0
		sbc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

inv_word	.proc
		lda  P8ESTACK_LO+1,x
		eor  #255
		sta  P8ESTACK_LO+1,x
		lda  P8ESTACK_HI+1,x
		eor  #255
		sta  P8ESTACK_HI+1,x
		rts
		.pend

not_byte	.proc
		lda  P8ESTACK_LO+1,x
		beq  +
		lda  #1
+		eor  #1
		sta  P8ESTACK_LO+1,x
		rts
		.pend

not_word	.proc
		lda  P8ESTACK_LO + 1,x
		ora  P8ESTACK_HI + 1,x
		beq  +
		lda  #1
+		eor  #1
		sta  P8ESTACK_LO + 1,x
		lsr  a
		sta  P8ESTACK_HI + 1,x
		rts
		.pend

bitand_b	.proc
		; -- bitwise and (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		and  P8ESTACK_LO+1,x
		inx
		sta  P8ESTACK_LO+1,x
		rts
		.pend

bitor_b		.proc
		; -- bitwise or (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		ora  P8ESTACK_LO+1,x
		inx
		sta  P8ESTACK_LO+1,x
		rts
		.pend

bitxor_b	.proc
		; -- bitwise xor (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		eor  P8ESTACK_LO+1,x
		inx
		sta  P8ESTACK_LO+1,x
		rts
		.pend

bitand_w	.proc
		; -- bitwise and (of 2 words)
		lda  P8ESTACK_LO+2,x
		and  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+2,x
		lda  P8ESTACK_HI+2,x
		and  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+2,x
		inx
		rts
		.pend

bitor_w		.proc
		; -- bitwise or (of 2 words)
		lda  P8ESTACK_LO+2,x
		ora  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+2,x
		lda  P8ESTACK_HI+2,x
		ora  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+2,x
		inx
		rts
		.pend

bitxor_w	.proc
		; -- bitwise xor (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		eor  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+2,x
		lda  P8ESTACK_HI+2,x
		eor  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+2,x
		inx
		rts
		.pend

and_b		.proc
		; -- logical and (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		beq  +
		lda  #1
+		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		beq  +
		lda  #1
+		and  P8ZP_SCRATCH_B1
		inx
		sta  P8ESTACK_LO+1,x
		rts
		.pend

or_b		.proc
		; -- logical or (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		ora  P8ESTACK_LO+1,x
		beq  +
		lda  #1
+		inx
		sta  P8ESTACK_LO+1,x
		rts
		.pend

xor_b		.proc
		; -- logical xor (of 2 bytes)
		lda  P8ESTACK_LO+2,x
		beq  +
		lda  #1
+		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		beq  +
		lda  #1
+		eor  P8ZP_SCRATCH_B1
		inx
		sta  P8ESTACK_LO+1,x
		rts
		.pend

and_w		.proc
		; -- logical and (word and word -> byte)
		lda  P8ESTACK_LO+2,x
		ora  P8ESTACK_HI+2,x
		beq  +
		lda  #1
+		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		ora  P8ESTACK_HI+1,x
		beq  +
		lda  #1
+		and  P8ZP_SCRATCH_B1
		inx
 		sta  P8ESTACK_LO+1,x
 		sta  P8ESTACK_HI+1,x
		rts
		.pend

or_w		.proc
		; -- logical or (word or word -> byte)
		lda  P8ESTACK_LO+2,x
		ora  P8ESTACK_LO+1,x
		ora  P8ESTACK_HI+2,x
		ora  P8ESTACK_HI+1,x
		beq  +
		lda  #1
+		inx
		sta  P8ESTACK_LO+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

xor_w		.proc
		; -- logical xor (word xor word -> byte)
		lda  P8ESTACK_LO+2,x
		ora  P8ESTACK_HI+2,x
		beq  +
		lda  #1
+		sta  P8ZP_SCRATCH_B1
		lda  P8ESTACK_LO+1,x
		ora  P8ESTACK_HI+1,x
		beq  +
		lda  #1
+		eor  P8ZP_SCRATCH_B1
		inx
 		sta  P8ESTACK_LO+1,x
 		sta  P8ESTACK_HI+1,x
		rts
		.pend


abs_b		.proc
	; -- push abs(byte) on stack (as byte)
		lda  P8ESTACK_LO+1,x
		bmi  neg_b
		rts
		.pend

abs_w		.proc
	; -- push abs(word) on stack (as word)
		lda  P8ESTACK_HI+1,x
		bmi  neg_w
		rts
		.pend

add_w		.proc
	; -- push word+word / uword+uword
		inx
		clc
		lda  P8ESTACK_LO,x
		adc  P8ESTACK_LO+1,x
		sta  P8ESTACK_LO+1,x
		lda  P8ESTACK_HI,x
		adc  P8ESTACK_HI+1,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

sub_w		.proc
	; -- push word-word
		inx
		sec
		lda  P8ESTACK_LO+1,x
		sbc  P8ESTACK_LO,x
		sta  P8ESTACK_LO+1,x
		lda  P8ESTACK_HI+1,x
		sbc  P8ESTACK_HI,x
		sta  P8ESTACK_HI+1,x
		rts
		.pend

mul_byte	.proc
	; -- b*b->b (signed and unsigned)
		inx
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_LO+1,x
		jsr  math.multiply_bytes
		sta  P8ESTACK_LO+1,x
		rts
		.pend

mul_word	.proc
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO+1,x
		ldy  P8ESTACK_HI+1,x
		jsr  math.multiply_words
		lda  math.multiply_words.result
		sta  P8ESTACK_LO+1,x
		lda  math.multiply_words.result+1
		sta  P8ESTACK_HI+1,x
		rts
		.pend

idiv_b		.proc
	; signed division: use unsigned division and fix sign of result afterwards
		inx
		lda  P8ESTACK_LO,x
		eor  P8ESTACK_LO+1,x
		php			; save sign of result
		lda  P8ESTACK_LO,x
		bpl  +
		eor  #$ff
		sec
		adc  #0			; make num1 positive
+		tay
		inx
		lda  P8ESTACK_LO,x
		bpl  +
		eor  #$ff
		sec
		adc  #0			; make num2 positive
+		jsr  math.divmod_ub_asm
		sta  _remainder
		tya
		plp			; get sign of result
		bpl  +
		eor  #$ff
		sec
		adc  #0			; negate result
+		sta  P8ESTACK_LO,x
		dex
		rts
_remainder	.byte  0
		.pend

idiv_ub		.proc
		inx
		ldy  P8ESTACK_LO,x
		lda  P8ESTACK_LO+1,x
		jsr  math.divmod_ub_asm
		tya
		sta  P8ESTACK_LO+1,x
		rts
		.pend

idiv_w		.proc
	; signed division: use unsigned division and fix sign of result afterwards
		lda  P8ESTACK_HI+2,x
		eor  P8ESTACK_HI+1,x
		php				; save sign of result
		lda  P8ESTACK_HI+1,x
		bpl  +
		jsr  neg_w			; make value positive
+		inx
		lda  P8ESTACK_HI+1,x
		bpl  +
		jsr  neg_w			; make value positive
+		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		jsr  math.divmod_uw_asm
		sta  P8ESTACK_LO+1,x
		tya
		sta  P8ESTACK_HI+1,x
		plp
		bpl  +
		jmp  neg_w		; negate result
+		rts
		.pend

idiv_uw		.proc
		inx
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		jsr  math.divmod_uw_asm
		sta  P8ESTACK_LO+1,x
		tya
		sta  P8ESTACK_HI+1,x
		rts
		.pend

remainder_ub	.proc
		inx
		ldy  P8ESTACK_LO,x	; right operand
		lda  P8ESTACK_LO+1,x  ; left operand
		jsr  math.divmod_ub_asm
		sta  P8ESTACK_LO+1,x
		rts
		.pend

remainder_uw	.proc
		inx
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		jsr  math.divmod_uw_asm
		lda  P8ZP_SCRATCH_W2
		sta  P8ESTACK_LO+1,x
		lda  P8ZP_SCRATCH_W2+1
		sta  P8ESTACK_HI+1,x
		rts
		.pend

equal_w		.proc
	; -- are the two words on the stack identical?
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		bne  equal_b._equal_b_false
		lda  P8ESTACK_HI+1,x
		cmp  P8ESTACK_HI+2,x
		bne  equal_b._equal_b_false
		beq  equal_b._equal_b_true
		.pend

notequal_b	.proc
	; -- are the two bytes on the stack different?
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		beq  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		.pend

notequal_w	.proc
	; -- are the two words on the stack different?
		lda  P8ESTACK_HI+1,x
		cmp  P8ESTACK_HI+2,x
		beq  notequal_b
		bne  equal_b._equal_b_true
		.pend

less_ub		.proc
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

less_b		.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  P8ESTACK_LO+2,x
		sec
		sbc  P8ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

less_uw		.proc
		lda  P8ESTACK_HI+2,x
		cmp  P8ESTACK_HI+1,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

less_w		.proc
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		lda  P8ESTACK_HI+2,x
		sbc  P8ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

equal_b		.proc
	; -- are the two bytes on the stack identical?
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		bne  _equal_b_false
_equal_b_true	lda  #1
_equal_b_store	inx
		sta  P8ESTACK_LO+1,x
		rts
_equal_b_false	lda  #0
		beq  _equal_b_store
		.pend

lesseq_ub	.proc
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

lesseq_b	.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  P8ESTACK_LO+2,x
		clc
		sbc  P8ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

lesseq_uw	.proc
		lda  P8ESTACK_HI+1,x
		cmp  P8ESTACK_HI+2,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

lesseq_w	.proc
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		lda  P8ESTACK_HI+1,x
		sbc  P8ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_ub	.proc
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		beq  equal_b._equal_b_false
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greater_b	.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  P8ESTACK_LO+2,x
		clc
		sbc  P8ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_uw	.proc
		lda  P8ESTACK_HI+1,x
		cmp  P8ESTACK_HI+2,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

greater_w	.proc
		lda  P8ESTACK_LO+1,x
		cmp  P8ESTACK_LO+2,x
		lda  P8ESTACK_HI+1,x
		sbc  P8ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

greatereq_ub	.proc
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greatereq_b	.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  P8ESTACK_LO+2,x
		sec
		sbc  P8ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greatereq_uw	.proc
		lda  P8ESTACK_HI+2,x
		cmp  P8ESTACK_HI+1,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greatereq_w	.proc
		lda  P8ESTACK_LO+2,x
		cmp  P8ESTACK_LO+1,x
		lda  P8ESTACK_HI+2,x
		sbc  P8ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend


shiftleft_b	.proc
		inx
		ldy  P8ESTACK_LO,x
		bne  +
		rts
+		lda  P8ESTACK_LO+1,x
-		asl  a
		dey
		bne  -
		sta  P8ESTACK_LO+1,x
		rts
		.pend

shiftright_b	.proc
		inx
		ldy  P8ESTACK_LO,x
		bne  +
		rts
+		lda  P8ESTACK_LO+1,x
-		lsr  a
		dey
		bne  -
		sta  P8ESTACK_LO+1,x
		rts
		.pend


orig_stackpointer	.byte  0	; stores the Stack pointer register at program start

func_exit	.proc
		; -- immediately exit the program with a return code in the A register
		lda  P8ESTACK_LO+1,x
		ldx  orig_stackpointer
		txs
		rts		; return to original caller
		.pend


func_read_flags	.proc
		; -- put the processor status register on the stack
		php
		pla
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend


func_sqrt16	.proc
		; TODO is this one faster?  http://6502org.wikidot.com/software-math-sqrt
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W2
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W2+1
		stx  P8ZP_SCRATCH_REG
		ldy  #$00    ; r = 0
		ldx  #$07
		clc         ; clear bit 16 of m
_loop
		tya
		ora  _stab-1,x
		sta  P8ZP_SCRATCH_B1     ; (r asl 8) | (d asl 7)
		lda  P8ZP_SCRATCH_W2+1
		bcs  _skip0  ; m >= 65536? then t <= m is always true
		cmp  P8ZP_SCRATCH_B1
		bcc  _skip1  ; t <= m
_skip0
		sbc  P8ZP_SCRATCH_B1
		sta  P8ZP_SCRATCH_W2+1     ; m = m - t
		tya
		ora  _stab,x
		tay         ; r = r or d
_skip1
		asl  P8ZP_SCRATCH_W2
		rol  P8ZP_SCRATCH_W2+1     ; m = m asl 1
		dex
		bne  _loop

		; last iteration
		bcs  _skip2
		sty  P8ZP_SCRATCH_B1
		lda  P8ZP_SCRATCH_W2
		cmp  #$80
		lda  P8ZP_SCRATCH_W2+1
		sbc  P8ZP_SCRATCH_B1
		bcc  _skip3
_skip2
		iny         ; r = r or d (d is 1 here)
_skip3
		ldx  P8ZP_SCRATCH_REG
		tya
		sta  P8ESTACK_LO+1,x
		lda  #0
		sta  P8ESTACK_HI+1,x
		rts
_stab   .byte $01,$02,$04,$08,$10,$20,$40,$80
		.pend


func_sin8	.proc
		ldy  P8ESTACK_LO+1,x
		lda  _sinecos8,y
		sta  P8ESTACK_LO+1,x
		rts
_sinecos8	.char  trunc(127.0 * sin(range(256+64) * rad(360.0/256.0)))
		.pend

func_sin8u	.proc
		ldy  P8ESTACK_LO+1,x
		lda  _sinecos8u,y
		sta  P8ESTACK_LO+1,x
		rts
_sinecos8u	.byte  trunc(128.0 + 127.5 * sin(range(256+64) * rad(360.0/256.0)))
		.pend

func_sin16	.proc
		ldy  P8ESTACK_LO+1,x
		lda  _sinecos8lo,y
		sta  P8ESTACK_LO+1,x
		lda  _sinecos8hi,y
		sta  P8ESTACK_HI+1,x
		rts

_  :=  trunc(32767.0 * sin(range(256+64) * rad(360.0/256.0)))
_sinecos8lo     .byte  <_
_sinecos8hi     .byte  >_
		.pend

func_sin16u	.proc
		ldy  P8ESTACK_LO+1,x
		lda  _sinecos8ulo,y
		sta  P8ESTACK_LO+1,x
		lda  _sinecos8uhi,y
		sta  P8ESTACK_HI+1,x
		rts

_  :=  trunc(32768.0 + 32767.5 * sin(range(256+64) * rad(360.0/256.0)))
_sinecos8ulo     .byte  <_
_sinecos8uhi     .byte  >_
		.pend

func_cos8	.proc
		ldy  P8ESTACK_LO+1,x
		lda  func_sin8._sinecos8+64,y
		sta  P8ESTACK_LO+1,x
		rts
		.pend

func_cos8u	.proc
		ldy  P8ESTACK_LO+1,x
		lda  func_sin8u._sinecos8u+64,y
		sta  P8ESTACK_LO+1,x
		rts
		.pend

func_cos16	.proc
		ldy  P8ESTACK_LO+1,x
		lda  func_sin16._sinecos8lo+64,y
		sta  P8ESTACK_LO+1,x
		lda  func_sin16._sinecos8hi+64,y
		sta  P8ESTACK_HI+1,x
		rts
		.pend

func_cos16u	.proc
		ldy  P8ESTACK_LO+1,x
		lda  func_sin16u._sinecos8ulo+64,y
		sta  P8ESTACK_LO+1,x
		lda  func_sin16u._sinecos8uhi+64,y
		sta  P8ESTACK_HI+1,x
		rts
		.pend


peek_address	.proc
	; -- peek address on stack into P8ZP_SCRATCH_W1
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		rts
		.pend

func_any_b	.proc
		inx
		lda  P8ESTACK_LO,x	; array size
_entry		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		bne  _got_any
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #0
		sta  P8ESTACK_LO+1,x
		rts
_got_any	lda  #1
		sta  P8ESTACK_LO+1,x
		rts
		.pend

func_any_w	.proc
		inx
		lda  P8ESTACK_LO,x	; array size
		asl  a			; times 2 because of word
		jmp  func_any_b._entry
		.pend

func_all_b	.proc
		inx
		lda  P8ESTACK_LO,x	; array size
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  _got_not_all
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  P8ESTACK_LO+1,x
		rts
_got_not_all	lda  #0
		sta  P8ESTACK_LO+1,x
		rts
		.pend

func_all_w	.proc
		inx
		lda  P8ESTACK_LO,x	; array size
		asl  a			; times 2 because of word
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		bne  +
		iny
		lda  (P8ZP_SCRATCH_W1),y
		bne  ++
		lda  #0
		sta  P8ESTACK_LO+1,x
		rts
+		iny
+		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  P8ESTACK_LO+1,x
		rts
		.pend

func_max_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_B1
		bcc  +
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_max_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #-128
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		sec
		sbc  P8ZP_SCRATCH_B1
		bvc  +
		eor  #$80
+		bmi  +
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_max_uw	.proc
		lda  #0
		sta  _result_maxuw
		sta  _result_maxuw+1
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
_loop
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		cmp  _result_maxuw+1
		bcc  _lesseq
		bne  _greater
		lda  (P8ZP_SCRATCH_W1),y
		cmp  _result_maxuw
		bcc  _lesseq
_greater	lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxuw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxuw+1
		dey
_lesseq		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_maxuw
		sta  P8ESTACK_LO,x
		lda  _result_maxuw+1
		sta  P8ESTACK_HI,x
		dex
		rts
_result_maxuw	.word  0
		.pend

func_max_w	.proc
		lda  #0
		sta  _result_maxw
		lda  #$80
		sta  _result_maxw+1
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
_loop
		lda  (P8ZP_SCRATCH_W1),y
		cmp  _result_maxw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		sbc  _result_maxw+1
		bvc  +
		eor  #$80
+		bmi  _lesseq
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxw+1
		dey
_lesseq		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_maxw
		sta  P8ESTACK_LO,x
		lda  _result_maxw+1
		sta  P8ESTACK_HI,x
		dex
		rts
_result_maxw	.word  0
		.pend


func_sum_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  P8ESTACK_LO,x
		sta  P8ESTACK_HI,x
_loop		lda  (P8ZP_SCRATCH_W1),y
		pha
		clc
		adc  P8ESTACK_LO,x
		sta  P8ESTACK_LO,x
		; sign extend the high byte
		pla
		and  #$80
		beq  +
		lda  #$ff
+		adc  P8ESTACK_HI,x
		sta  P8ESTACK_HI,x
		dey
		cpy  #255
		bne  _loop
		dex
		rts
		.pend

func_sum_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  P8ESTACK_HI,x
-		clc
		adc  (P8ZP_SCRATCH_W1),y
		bcc  +
		inc  P8ESTACK_HI,x
+		dey
		cpy  #255
		bne  -
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sum_uw	.proc
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
		lda  #0
		sta  P8ESTACK_LO,x
		sta  P8ESTACK_HI,x
-		lda  (P8ZP_SCRATCH_W1),y
		iny
		clc
		adc  P8ESTACK_LO,x
		sta  P8ESTACK_LO,x
		lda  (P8ZP_SCRATCH_W1),y
		adc  P8ESTACK_HI,x
		sta  P8ESTACK_HI,x
		dey
		dey
		dey
		cpy  #254
		bne  -
		dex
		rts
		.pend

func_sum_w	.proc
		jmp  func_sum_uw
		.pend


pop_array_and_lengthmin1Y	.proc
		inx
		ldy  P8ESTACK_LO,x
		dey				; length minus 1, for iteration
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W1+1
		inx
		rts
		.pend

func_min_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #255
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_B1
		bcs  +
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend


func_min_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #127
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		clc
		sbc  P8ZP_SCRATCH_B1
		bvc  +
		eor  #$80
+		bpl  +
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_min_uw	.proc
		lda  #$ff
		sta  _result_minuw
		sta  _result_minuw+1
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
_loop
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		cmp   _result_minuw+1
		bcc  _less
		bne  _gtequ
		lda  (P8ZP_SCRATCH_W1),y
		cmp  _result_minuw
		bcs  _gtequ
_less		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minuw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minuw+1
		dey
_gtequ		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_minuw
		sta  P8ESTACK_LO,x
		lda  _result_minuw+1
		sta  P8ESTACK_HI,x
		dex
		rts
_result_minuw	.word  0
		.pend

func_min_w	.proc
		lda  #$ff
		sta  _result_minw
		lda  #$7f
		sta  _result_minw+1
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
_loop
		lda  (P8ZP_SCRATCH_W1),y
		cmp   _result_minw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		sbc  _result_minw+1
		bvc  +
		eor  #$80
+		bpl  _gtequ
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minw+1
		dey
_gtequ		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_minw
		sta  P8ESTACK_LO,x
		lda  _result_minw+1
		sta  P8ESTACK_HI,x
		dex
		rts
_result_minw	.word  0
		.pend

func_rnd	.proc
	; -- put a random ubyte on the estack
		jsr  math.randbyte
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_rndw	.proc
	; -- put a random uword on the estack
		jsr  math.randword
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend


func_memcopy	.proc
	; note: clobbers A,Y
		inx
		stx  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+2,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+2,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W2
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W2+1
		lda  P8ESTACK_LO,x
		tax
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1), y
		sta  (P8ZP_SCRATCH_W2), y
		iny
		dex
		bne  -
		ldx  P8ZP_SCRATCH_REG
		inx
		inx
		rts
		.pend

func_memset	.proc
	; note: clobbers A,Y
		inx
		stx  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+2,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+2,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_B1
		ldy  P8ESTACK_HI+1,x
		lda  P8ESTACK_LO,x
		ldx  P8ZP_SCRATCH_B1
		jsr  memset
		ldx  P8ZP_SCRATCH_REG
		inx
		inx
		rts
		.pend

func_memsetw	.proc
	; note: clobbers A,Y
		; -- fill memory from (SCRATCH_ZPWORD1) number of words in SCRATCH_ZPWORD2, with word value in AY.

		inx
		stx  P8ZP_SCRATCH_REG
		lda  P8ESTACK_LO+2,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+2,x
		sta  P8ZP_SCRATCH_W1+1
		lda  P8ESTACK_LO+1,x
		sta  P8ZP_SCRATCH_W2
		lda  P8ESTACK_HI+1,x
		sta  P8ZP_SCRATCH_W2+1
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		jsr  memsetw
		ldx  P8ZP_SCRATCH_REG
		inx
		inx
		rts
		.pend

strlen		.proc
	; -- put length of 0-terminated string in A/Y into A
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		iny
		bne  -
+		tya
		rts
		.pend


memcopy16_up	.proc
	; -- copy memory UP from (SCRATCH_ZPWORD1) to (SCRATCH_ZPWORD2) of length X/Y (16-bit, X=lo, Y=hi)
	;    clobbers register A,X,Y
		source = P8ZP_SCRATCH_W1
		dest = P8ZP_SCRATCH_W2
		length = P8ZP_SCRATCH_B1   ; (and SCRATCH_ZPREG)

		stx  length
		sty  length+1

		ldx  length             ; move low byte of length into X
		bne  +                  ; jump to start if X > 0
		dec  length             ; subtract 1 from length
+		ldy  #0                 ; set Y to 0
-		lda  (source),y         ; set A to whatever (source) points to offset by Y
		sta  (dest),y           ; move A to location pointed to by (dest) offset by Y
		iny                     ; increment Y
		bne  +                  ; if Y<>0 then (rolled over) then still moving bytes
		inc  source+1           ; increment hi byte of source
		inc  dest+1             ; increment hi byte of dest
+		dex                     ; decrement X (lo byte counter)
		bne  -                  ; if X<>0 then move another byte
		dec  length             ; we've moved 255 bytes, dec length
		bpl  -                  ; if length is still positive go back and move more
		rts                     ; done
		.pend


memset          .proc
	; -- fill memory from (SCRATCH_ZPWORD1), length XY, with value in A.
	;    clobbers X, Y
		stx  P8ZP_SCRATCH_B1
		sty  _save_reg
		ldy  #0
		ldx  _save_reg
		beq  _lastpage

_fullpage	sta  (P8ZP_SCRATCH_W1),y
		iny
		bne  _fullpage
		inc  P8ZP_SCRATCH_W1+1          ; next page
		dex
		bne  _fullpage

_lastpage	ldy  P8ZP_SCRATCH_B1
		beq  +
-         	dey
		sta  (P8ZP_SCRATCH_W1),y
		bne  -

+           	rts
_save_reg	.byte  0
		.pend


memsetw		.proc
	; -- fill memory from (SCRATCH_ZPWORD1) number of words in SCRATCH_ZPWORD2, with word value in AY.
	;    clobbers A, X, Y
		sta  _mod1+1                    ; self-modify
		sty  _mod1b+1                   ; self-modify
		sta  _mod2+1                    ; self-modify
		sty  _mod2b+1                   ; self-modify
		ldx  P8ZP_SCRATCH_W1
		stx  P8ZP_SCRATCH_B1
		ldx  P8ZP_SCRATCH_W1+1
		inx
		stx  P8ZP_SCRATCH_REG                ; second page

		ldy  #0
		ldx  P8ZP_SCRATCH_W2+1
		beq  _lastpage

_fullpage
_mod1           lda  #0                         ; self-modified
		sta  (P8ZP_SCRATCH_W1),y        ; first page
		sta  (P8ZP_SCRATCH_B1),y            ; second page
		iny
_mod1b		lda  #0                         ; self-modified
		sta  (P8ZP_SCRATCH_W1),y        ; first page
		sta  (P8ZP_SCRATCH_B1),y            ; second page
		iny
		bne  _fullpage
		inc  P8ZP_SCRATCH_W1+1          ; next page pair
		inc  P8ZP_SCRATCH_W1+1          ; next page pair
		inc  P8ZP_SCRATCH_B1+1              ; next page pair
		inc  P8ZP_SCRATCH_B1+1              ; next page pair
		dex
		bne  _fullpage

_lastpage	ldx  P8ZP_SCRATCH_W2
		beq  _done

		ldy  #0
-
_mod2           lda  #0                         ; self-modified
                sta  (P8ZP_SCRATCH_W1), y
		inc  P8ZP_SCRATCH_W1
		bne  _mod2b
		inc  P8ZP_SCRATCH_W1+1
_mod2b          lda  #0                         ; self-modified
		sta  (P8ZP_SCRATCH_W1), y
		inc  P8ZP_SCRATCH_W1
		bne  +
		inc  P8ZP_SCRATCH_W1+1
+               dex
		bne  -
_done		rts
		.pend


sort_ub		.proc
		; 8bit unsigned sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in P8ZP_SCRATCH_B1
		; first, put pointer BEFORE array
		lda  P8ZP_SCRATCH_W1
		bne  +
		dec  P8ZP_SCRATCH_W1+1
+		dec  P8ZP_SCRATCH_W1
_sortloop	ldy  P8ZP_SCRATCH_B1		;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y	;last value in (what is left of) sequence to be sorted
		sta  P8ZP_SCRATCH_REG		;save value. will be over-written by largest number
		jmp  _l2
_l1		dey
		beq  _l3
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2+1
		bcc  _l1
_l2		sty  P8ZP_SCRATCH_W2	;index of potentially largest value
		sta  P8ZP_SCRATCH_W2+1	;potentially largest value
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1		;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2+1	;the largest value
		sta  (P8ZP_SCRATCH_W1),y	;put largest value in place
		ldy  P8ZP_SCRATCH_W2	;index of free space
		lda  P8ZP_SCRATCH_REG		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y	;put the over-written value in the free space
		dec  P8ZP_SCRATCH_B1		;end of the shorter sequence still left
		bne  _sortloop			;start working with the shorter sequence
		rts
		.pend


sort_b		.proc
		; 8bit signed sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in P8ZP_SCRATCH_B1
		; first, put pointer BEFORE array
		lda  P8ZP_SCRATCH_W1
		bne  +
		dec  P8ZP_SCRATCH_W1+1
+		dec  P8ZP_SCRATCH_W1
_sortloop	ldy  P8ZP_SCRATCH_B1		;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y	;last value in (what is left of) sequence to be sorted
		sta  P8ZP_SCRATCH_REG		;save value. will be over-written by largest number
		jmp  _l2
_l1		dey
		beq  _l3
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2+1
		bmi  _l1
_l2		sty  P8ZP_SCRATCH_W2	;index of potentially largest value
		sta  P8ZP_SCRATCH_W2+1	;potentially largest value
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1		;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2+1	;the largest value
		sta  (P8ZP_SCRATCH_W1),y	;put largest value in place
		ldy  P8ZP_SCRATCH_W2	;index of free space
		lda  P8ZP_SCRATCH_REG		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y	;put the over-written value in the free space
		dec  P8ZP_SCRATCH_B1		;end of the shorter sequence still left
		bne  _sortloop			;start working with the shorter sequence
		rts
		.pend


sort_uw		.proc
		; 16bit unsigned sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in P8ZP_SCRATCH_B1
		; first: subtract 2 of the pointer
		asl  P8ZP_SCRATCH_B1		; *2 because words
		lda  P8ZP_SCRATCH_W1
		sec
		sbc  #2
		sta  P8ZP_SCRATCH_W1
		bcs  _sort_loop
		dec  P8ZP_SCRATCH_W1+1
_sort_loop	ldy  P8ZP_SCRATCH_B1    	;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y    ;last value in (what is left of) sequence to be sorted
		sta  _work3          		;save value. will be over-written by largest number
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _work3+1
		dey
		jmp  _l2
_l1		dey
		dey
		beq  _l3
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		cmp  P8ZP_SCRATCH_W2+1
		bne  +
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2
+		bcc  _l1
_l2		sty  _work1          		;index of potentially largest value
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2          ;potentially largest value
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2+1
		dey
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1           ;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2          ;the largest value
		sta  (P8ZP_SCRATCH_W1),y      ;put largest value in place
		iny
		lda  P8ZP_SCRATCH_W2+1
		sta  (P8ZP_SCRATCH_W1),y
		ldy  _work1         		 ;index of free space
		lda  _work3          		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y      ;put the over-written value in the free space
		iny
		lda  _work3+1
		sta  (P8ZP_SCRATCH_W1),y
		dey
		dec  P8ZP_SCRATCH_B1           ;end of the shorter sequence still left
		dec  P8ZP_SCRATCH_B1
		bne  _sort_loop           ;start working with the shorter sequence
		rts
_work1	.byte  0
_work3	.word  0
		.pend


sort_w		.proc
		; 16bit signed sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in P8ZP_SCRATCH_B1
		; first: subtract 2 of the pointer
		asl  P8ZP_SCRATCH_B1		; *2 because words
		lda  P8ZP_SCRATCH_W1
		sec
		sbc  #2
		sta  P8ZP_SCRATCH_W1
		bcs  _sort_loop
		dec  P8ZP_SCRATCH_W1+1
_sort_loop	ldy  P8ZP_SCRATCH_B1    	;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y    ;last value in (what is left of) sequence to be sorted
		sta  _work3          		;save value. will be over-written by largest number
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _work3+1
		dey
		jmp  _l2
_l1		dey
		dey
		beq  _l3
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		sbc  P8ZP_SCRATCH_W2+1
		bvc  +
		eor  #$80
+		bmi  _l1
_l2		sty  _work1          		;index of potentially largest value
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2          ;potentially largest value
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2+1
		dey
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1           ;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2          ;the largest value
		sta  (P8ZP_SCRATCH_W1),y      ;put largest value in place
		iny
		lda  P8ZP_SCRATCH_W2+1
		sta  (P8ZP_SCRATCH_W1),y
		ldy  _work1         		 ;index of free space
		lda  _work3          		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y      ;put the over-written value in the free space
		iny
		lda  _work3+1
		sta  (P8ZP_SCRATCH_W1),y
		dey
		dec  P8ZP_SCRATCH_B1           ;end of the shorter sequence still left
		dec  P8ZP_SCRATCH_B1
		bne  _sort_loop           ;start working with the shorter sequence
		rts
_work1	.byte  0
_work3	.word  0
		.pend


reverse_b	.proc
		; --- reverse an array of bytes (in-place)
		; inputs:  pointer to array in P8ZP_SCRATCH_W1, length in A
_index_right = P8ZP_SCRATCH_W2
_index_left = P8ZP_SCRATCH_W2+1
_loop_count = P8ZP_SCRATCH_REG
		sta  _loop_count
		lsr  _loop_count
		sec
		sbc  #1
		sta  _index_right
		lda  #0
		sta  _index_left
_loop		ldy  _index_right
		lda  (P8ZP_SCRATCH_W1),y
		pha
		ldy  _index_left
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _index_right
		sta  (P8ZP_SCRATCH_W1),y
		pla
		ldy  _index_left
		sta  (P8ZP_SCRATCH_W1),y
		inc  _index_left
		dec  _index_right
		dec  _loop_count
		bne  _loop
		rts
		.pend


reverse_f	.proc
		; --- reverse an array of floats
_left_index = P8ZP_SCRATCH_W2
_right_index = P8ZP_SCRATCH_W2+1
_loop_count = P8ZP_SCRATCH_REG
		pha
		sta  P8ZP_SCRATCH_REG
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_REG		; *5 because float
		sec
		sbc  #5
		sta  _right_index
		lda  #0
		sta  _left_index
		pla
		lsr  a
		sta  _loop_count
_loop		; push the left indexed float on the stack
		ldy  _left_index
		lda  (P8ZP_SCRATCH_W1),y
		pha
		iny
		lda  (P8ZP_SCRATCH_W1),y
		pha
		iny
		lda  (P8ZP_SCRATCH_W1),y
		pha
		iny
		lda  (P8ZP_SCRATCH_W1),y
		pha
		iny
		lda  (P8ZP_SCRATCH_W1),y
		pha
		; copy right index float to left index float
		ldy  _right_index
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _left_index
		sta  (P8ZP_SCRATCH_W1),y
		inc  _left_index
		inc  _right_index
		ldy  _right_index
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _left_index
		sta  (P8ZP_SCRATCH_W1),y
		inc  _left_index
		inc  _right_index
		ldy  _right_index
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _left_index
		sta  (P8ZP_SCRATCH_W1),y
		inc  _left_index
		inc  _right_index
		ldy  _right_index
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _left_index
		sta  (P8ZP_SCRATCH_W1),y
		inc  _left_index
		inc  _right_index
		ldy  _right_index
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _left_index
		sta  (P8ZP_SCRATCH_W1),y
		; pop the float off the stack into the right index float
		ldy  _right_index
		pla
		sta  (P8ZP_SCRATCH_W1),y
		dey
		pla
		sta  (P8ZP_SCRATCH_W1),y
		dey
		pla
		sta  (P8ZP_SCRATCH_W1),y
		dey
		pla
		sta  (P8ZP_SCRATCH_W1),y
		dey
		pla
		sta  (P8ZP_SCRATCH_W1),y
		inc  _left_index
		lda  _right_index
		sec
		sbc  #9
		sta  _right_index
		dec  _loop_count
		bne  _loop
		rts

		.pend


reverse_w	.proc
		; --- reverse an array of words (in-place)
		; inputs:  pointer to array in P8ZP_SCRATCH_W1, length in A
_index_first = P8ZP_SCRATCH_W2
_index_second = P8ZP_SCRATCH_W2+1
_loop_count = P8ZP_SCRATCH_REG
		pha
		asl  a     ; *2 because words
		sec
		sbc  #2
		sta  _index_first
		lda  #0
		sta  _index_second
		pla
		lsr  a
		pha
		sta  _loop_count
		; first reverse the lsbs
_loop_lo	ldy  _index_first
		lda  (P8ZP_SCRATCH_W1),y
		pha
		ldy  _index_second
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _index_first
		sta  (P8ZP_SCRATCH_W1),y
		pla
		ldy  _index_second
		sta  (P8ZP_SCRATCH_W1),y
		inc  _index_second
		inc  _index_second
		dec  _index_first
		dec  _index_first
		dec  _loop_count
		bne  _loop_lo
		; now reverse the msbs
		dec  _index_second
		inc  _index_first
		inc  _index_first
		inc  _index_first
		pla
		sta  _loop_count
_loop_hi	ldy  _index_first
		lda  (P8ZP_SCRATCH_W1),y
		pha
		ldy  _index_second
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _index_first
		sta  (P8ZP_SCRATCH_W1),y
		pla
		ldy  _index_second
		sta  (P8ZP_SCRATCH_W1),y
		dec  _index_second
		dec  _index_second
		inc  _index_first
		inc  _index_first
		dec  _loop_count
		bne  _loop_hi

		rts
		.pend

ror2_mem_ub	.proc
		; -- in-place 8-bit ror of byte at memory location on stack
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		bcc  +
		ora  #$80
+		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol2_mem_ub	.proc
		; -- in-place 8-bit rol of byte at memory location on stack
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #$80
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol_array_ub	.proc
		; -- rol a ubyte in an array (index and array address on stack)
		inx
		ldy  P8ESTACK_LO,x
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  (P8ZP_SCRATCH_W1),y
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend


ror_array_ub	.proc
		; -- ror a ubyte in an array (index and array address on stack)
		inx
		ldy  P8ESTACK_LO,x
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  (P8ZP_SCRATCH_W1),y
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

ror2_array_ub	.proc
		; -- ror2 (8-bit ror) a ubyte in an array (index and array address on stack)
		inx
		ldy  P8ESTACK_LO,x
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		bcc  +
		ora  #$80
+		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol2_array_ub	.proc
		; -- rol2 (8-bit rol) a ubyte in an array (index and array address on stack)
		inx
		ldy  P8ESTACK_LO,x
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #$80
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

ror_array_uw	.proc
		; -- ror a uword in an array (index and array address on stack)
		php
		inx
		lda  P8ESTACK_LO,x
		asl  a
		tay
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		iny
		lda  (P8ZP_SCRATCH_W1),y
		plp
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		dey
		lda  (P8ZP_SCRATCH_W1),y
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol_array_uw	.proc
		; -- rol a uword in an array (index and array address on stack)
		php
		inx
		lda  P8ESTACK_LO,x
		asl  a
		tay
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  (P8ZP_SCRATCH_W1),y
		plp
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol2_array_uw	.proc
		; -- rol2 (16-bit rol) a uword in an array (index and array address on stack)
		inx
		lda  P8ESTACK_LO,x
		asl  a
		tay
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  (P8ZP_SCRATCH_W1),y
		asl  a
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		bcc  +
		dey
		lda  (P8ZP_SCRATCH_W1),y
		adc  #0
		sta  (P8ZP_SCRATCH_W1),y
+		rts
		.pend

ror2_array_uw	.proc
		; -- ror2 (16-bit ror) a uword in an array (index and array address on stack)
		inx
		lda  P8ESTACK_LO,x
		asl  a
		tay
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		iny
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		sta  (P8ZP_SCRATCH_W1),y
		dey
		lda  (P8ZP_SCRATCH_W1),y
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		bcc  +
		iny
		lda  (P8ZP_SCRATCH_W1),y
		ora  #$80
		sta  (P8ZP_SCRATCH_W1),y
+		rts
		.pend


strcpy		.proc
		; copy a string (must be 0-terminated) from A/Y to (P8ZP_SCRATCH_W1)
		; it is assumed the target string is large enough.
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #$ff
-		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		bne  -
		rts
		.pend


strcmp_mem	.proc
		; --   compares strings in s1 (AY) and s2 (PZP_SCRATCH_W2).
		;      Returns -1,0,1 in A, depeding on the ordering. Clobbers Y.
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
_loop       ldy  #0
            lda  (P8ZP_SCRATCH_W1),y
            bne  +
            lda  (P8ZP_SCRATCH_W2),y
            bne  _return_minusone
            beq  _return
+           lda  (P8ZP_SCRATCH_W2),y
            sec
            sbc  (P8ZP_SCRATCH_W1),y
            bmi  _return_one
            bne  _return_minusone
            inc  P8ZP_SCRATCH_W1
            bne  +
            inc  P8ZP_SCRATCH_W1+1
+           inc  P8ZP_SCRATCH_W2
            bne  _loop
            inc  P8ZP_SCRATCH_W2+1
            bne  _loop
_return_one
            lda  #1
_return     rts
_return_minusone
            lda  #-1
            rts
            .pend


func_leftstr	.proc
		; leftstr(source, target, length) with params on stack
		inx
		lda  P8ESTACK_LO,x
		tay			; length
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W2
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W2+1
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		lda  #0
		sta  (P8ZP_SCRATCH_W2),y
-		dey
		cpy  #$ff
		bne  +
		rts
+		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		jmp  -
		.pend

func_rightstr	.proc
		; rightstr(source, target, length) with params on stack
		; make place for the 4 parameters for substr()
		dex
		dex
		dex
		dex
		; X-> .
		; x+1 -> length of segment
		; x+2 -> start index
		; X+3 -> target LO+HI
		; X+4 -> source LO+HI
		; original parameters:
		;  x+5 -> original length LO
		;  x+6 -> original targetLO + HI
		;  x+7 -> original sourceLO + HI
		; replicate paramters:
		lda  P8ESTACK_LO+5,x
		sta  P8ESTACK_LO+1,x
		lda  P8ESTACK_LO+6,x
		sta  P8ESTACK_LO+3,x
		lda  P8ESTACK_HI+6,x
		sta  P8ESTACK_HI+3,x
		lda  P8ESTACK_LO+7,x
		sta  P8ESTACK_LO+4,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI+7,x
		sta  P8ESTACK_HI+4,x
		sta  P8ZP_SCRATCH_W1+1
		; determine string length
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		iny
		bne  -
+		tya
		sec
		sbc  P8ESTACK_LO+1,x  ; start index = strlen - segment length
		sta  P8ESTACK_LO+2,x
		jsr  func_substr
		; unwind original params
		inx
		inx
		inx
		rts
		.pend

func_substr	.proc
		; substr(source, target, start, length) with params on stack
		inx
		ldy  P8ESTACK_LO,x	; length
		inx
		lda  P8ESTACK_LO,x	; start
		sta  P8ZP_SCRATCH_B1
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W2
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W2+1
		inx
		lda  P8ESTACK_LO,x
		sta  P8ZP_SCRATCH_W1
		lda  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W1+1
		; adjust src location
		clc
		lda  P8ZP_SCRATCH_W1
		adc  P8ZP_SCRATCH_B1
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		lda  #0
		sta  (P8ZP_SCRATCH_W2),y
		jmp  _startloop
-		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
_startloop	dey
		cpy  #$ff
		bne  -
		rts

		.pend
