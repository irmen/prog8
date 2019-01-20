; Prog8 internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


init_system	.proc
	; -- initializes the machine to a sane starting state
	; Called automatically by the loader program logic.
	; This means that the BASIC, KERNAL and CHARGEN ROMs are banked in,
	; the VIC, SID and CIA chips are reset, screen is cleared, and the default IRQ is set.
	; Also a different color scheme is chosen to identify ourselves a little.
	; Uppercase charset is activated, and all three registers set to 0, status flags cleared.
		sei
		cld
		lda  #%00101111
		sta  $00
		lda  #%00100111
		sta  $01
		jsr  c64.IOINIT
		jsr  c64.RESTOR
		jsr  c64.CINT
		lda  #6
		sta  c64.EXTCOL
		lda  #7
		sta  c64.COLOR
		lda  #0
		sta  c64.BGCOL0
		tax
		tay
		clc
		clv
		cli
		rts
		.pend


add_a_to_zpword	.proc
	; -- add ubyte in A to the uword in c64.SCRATCH_ZPWORD1
		clc
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.SCRATCH_ZPWORD1
		bcc  +
		inc  c64.SCRATCH_ZPWORD1+1
+		rts
		.pend

pop_index_times_5	.proc
		inx
		lda  c64.ESTACK_LO,x
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1	; A*=5
		rts
		.pend

neg_b		.proc
		lda  c64.ESTACK_LO+1,x
		eor  #255
		clc
		adc  #1
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

neg_w		.proc
		sec
		lda  #0
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  #0
		sbc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

inv_word	.proc
		lda  c64.ESTACK_LO+1,x
		eor  #255
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		eor  #255
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

not_byte	.proc
		lda  c64.ESTACK_LO+1,x
		beq  +
		lda  #0
		beq ++
+		lda  #1
+		sta  c64.ESTACK_LO+1,x
		rts
		.pend

not_word	.proc
		lda  c64.ESTACK_LO + 1,x
		ora  c64.ESTACK_HI + 1,x
		beq  +
		lda  #0
		beq  ++
+		lda  #1
+		sta  c64.ESTACK_LO + 1,x
		sta  c64.ESTACK_HI + 1,x
		rts
		.pend

abs_b		.proc
	; -- push abs(byte) on stack (as byte)
		lda  c64.ESTACK_LO+1,x
		bmi  neg_b
		rts
		.pend

abs_w		.proc
	; -- push abs(word) on stack (as word)
		lda  c64.ESTACK_HI+1,x
		bmi  neg_w
		rts
		.pend

add_w		.proc
	; -- push word+word / uword+uword
		inx
		clc
		lda  c64.ESTACK_LO,x
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI,x
		adc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

sub_w		.proc
	; -- push word-word
		inx
		sec
		lda  c64.ESTACK_LO+1,x
		sbc  c64.ESTACK_LO,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		sbc  c64.ESTACK_HI,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte	.proc
	; -- b*b->b (signed and unsigned)
		inx
		lda  c64.ESTACK_LO,x
		ldy  c64.ESTACK_LO+1,x
		jsr  math.multiply_bytes
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word	.proc
		inx
		lda  c64.ESTACK_LO,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI,x
		sta  c64.SCRATCH_ZPWORD1+1
		lda  c64.ESTACK_LO+1,x
		ldy  c64.ESTACK_HI+1,x
		stx  c64.SCRATCH_ZPREGX
		jsr  math.multiply_words
		ldx  c64.SCRATCH_ZPREGX
		lda  math.multiply_words.multiply_words_result
		sta  c64.ESTACK_LO+1,x
		lda  math.multiply_words.multiply_words_result+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

idiv_b		.proc
	; signed division: use unsigned division and fix sign of result afterwards
		inx
		lda  c64.ESTACK_LO,x
		eor  c64.ESTACK_LO+1,x
		php			; save sign of result
		lda  c64.ESTACK_LO,x
		bpl  +
		eor  #$ff
		sec
		adc  #0			; make num1 positive
+		tay
		inx
		lda  c64.ESTACK_LO,x
		bpl  +
		eor  #$ff
		sec
		adc  #0			; make num2 positive
+		jsr  math.divmod_ub
		sta  _remainder
		tya
		plp			; get sign of result
		bpl  +
		eor  #$ff
		sec
		adc  #0			; negate result
+		sta  c64.ESTACK_LO,x
		dex
		rts
_remainder	.byte  0
		.pend

idiv_ub		.proc
		inx
		ldy  c64.ESTACK_LO,x
		lda  c64.ESTACK_LO+1,x
		jsr  math.divmod_ub
		tya
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

idiv_w		.proc
	; signed division: use unsigned division and fix sign of result afterwards
		lda  c64.ESTACK_HI+2,x
		eor  c64.ESTACK_HI+1,x
		php				; save sign of result
		lda  c64.ESTACK_HI+1,x
		bpl  +
		jsr  neg_w			; make value positive
+		inx
		lda  c64.ESTACK_HI+1,x
		bpl  +
		jsr  neg_w			; make value positive
+		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		lda  c64.ESTACK_LO,x
		ldy  c64.ESTACK_HI,x
		jsr  math.divmod_uw_asm
		sta  c64.ESTACK_LO+1,x
		tya
		sta  c64.ESTACK_HI+1,x
		plp
		bpl  +
		jmp  neg_w		; negate result
+		rts
		.pend

idiv_uw		.proc
		inx
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		lda  c64.ESTACK_LO,x
		ldy  c64.ESTACK_HI,x
		jsr  math.divmod_uw_asm
		sta  c64.ESTACK_LO+1,x
		tya
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

remainder_ub	.proc
		inx
		ldy  c64.ESTACK_LO,x	; right operand
		lda  c64.ESTACK_LO+1,x  ; left operand
		jsr  math.divmod_ub
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

remainder_uw	.proc
		inx
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		lda  c64.ESTACK_LO,x
		ldy  c64.ESTACK_HI,x
		jsr  math.divmod_uw_asm
		lda  c64.SCRATCH_ZPWORD2
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD2+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

equal_w		.proc
	; -- are the two words on the stack identical?
		lda  c64.ESTACK_LO+1,x
		cmp  c64.ESTACK_LO+2,x
		bne  equal_b._equal_b_false
		lda  c64.ESTACK_HI+1,x
		cmp  c64.ESTACK_HI+2,x
		bne  equal_b._equal_b_false
		beq  equal_b._equal_b_true
		.pend

notequal_b	.proc
	; -- are the two bytes on the stack different?
		inx
		lda  c64.ESTACK_LO,x
		eor  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

notequal_w	.proc
	; -- are the two words on the stack different?
		inx
		lda  c64.ESTACK_LO,x
		eor  c64.ESTACK_LO+1,x
		beq  +
		sta  c64.ESTACK_LO+1,x
		rts
+		lda  c64.ESTACK_HI,x
		eor  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

less_ub		.proc
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

less_b		.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  c64.ESTACK_LO+2,x
		sec
		sbc  c64.ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

less_uw		.proc
		lda  c64.ESTACK_HI+2,x
		cmp  c64.ESTACK_HI+1,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

less_w		.proc
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+2,x
		sbc  c64.ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

equal_b		.proc
	; -- are the two bytes on the stack identical?
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		bne  _equal_b_false
_equal_b_true	lda  #1
_equal_b_store	inx
		sta  c64.ESTACK_LO+1,x
		rts
_equal_b_false	lda  #0
		beq  _equal_b_store
		.pend

lesseq_ub	.proc
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		beq  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

lesseq_b	.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  c64.ESTACK_LO+2,x
		clc
		sbc  c64.ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

lesseq_uw	.proc
		lda  c64.ESTACK_HI+1,x
		cmp  c64.ESTACK_HI+2,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  c64.ESTACK_LO+1,x
		cmp  c64.ESTACK_LO+2,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

lesseq_w	.proc
		lda  c64.ESTACK_LO+1,x
		cmp  c64.ESTACK_LO+2,x
		lda  c64.ESTACK_HI+1,x
		sbc  c64.ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_ub	.proc
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		beq  equal_b._equal_b_false
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greater_b	.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  c64.ESTACK_LO+2,x
		clc
		sbc  c64.ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_uw	.proc
		lda  c64.ESTACK_HI+1,x
		cmp  c64.ESTACK_HI+2,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  c64.ESTACK_LO+1,x
		cmp  c64.ESTACK_LO+2,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

greater_w	.proc
		lda  c64.ESTACK_LO+1,x
		cmp  c64.ESTACK_LO+2,x
		lda  c64.ESTACK_HI+1,x
		sbc  c64.ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

greatereq_ub	.proc
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greatereq_b	.proc
	; see http://www.6502.org/tutorials/compare_beyond.html
		lda  c64.ESTACK_LO+2,x
		sec
		sbc  c64.ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greatereq_uw	.proc
		lda  c64.ESTACK_HI+2,x
		cmp  c64.ESTACK_HI+1,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greatereq_w	.proc
		lda  c64.ESTACK_LO+2,x
		cmp  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+2,x
		sbc  c64.ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend


func_sin8	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  _sinecos8,y
		sta  c64.ESTACK_LO+1,x
		rts
_sinecos8	.char  127 * sin(range(256+64) * rad(360.0/256.0))
		.pend

func_sin8u	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  _sinecos8u,y
		sta  c64.ESTACK_LO+1,x
		rts
_sinecos8u	.byte  128 + 127.5 * sin(range(256+64) * rad(360.0/256.0))
		.pend

func_sin16	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  _sinecos8lo,y
		sta  c64.ESTACK_LO+1,x
		lda  _sinecos8hi,y
		sta  c64.ESTACK_HI+1,x
		rts

_  :=  32767 * sin(range(256+64) * rad(360.0/256.0))
_sinecos8lo     .byte  <_
_sinecos8hi     .byte  >_
		.pend

func_sin16u	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  _sinecos8ulo,y
		sta  c64.ESTACK_LO+1,x
		lda  _sinecos8uhi,y
		sta  c64.ESTACK_HI+1,x
		rts

_  :=  32768 + 32767.5 * sin(range(256+64) * rad(360.0/256.0))
_sinecos8ulo     .byte  <_
_sinecos8uhi     .byte  >_
		.pend

func_cos8	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  func_sin8._sinecos8+64,y
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_cos8u	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  func_sin8u._sinecos8u+64,y
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_cos16	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  func_sin16._sinecos8lo+64,y
		sta  c64.ESTACK_LO+1,x
		lda  func_sin16._sinecos8hi+64,y
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

func_cos16u	.proc
		ldy  c64.ESTACK_LO+1,x
		lda  func_sin16u._sinecos8ulo+64,y
		sta  c64.ESTACK_LO+1,x
		lda  func_sin16u._sinecos8uhi+64,y
		sta  c64.ESTACK_HI+1,x
		rts
		.pend


peek_address	.proc
	; -- peek address on stack into c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		rts
		.pend

func_any_b	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
_entry		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (c64.SCRATCH_ZPWORD1),y
		bne  _got_any
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #0
		sta  c64.ESTACK_LO+1,x
		rts
_got_any	lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_any_w	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
		asl  a			; times 2 because of word
		jmp  func_any_b._entry
		.pend

func_all_b	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (c64.SCRATCH_ZPWORD1),y
		beq  _got_not_all
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
_got_not_all	lda  #0
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_all_w	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
		asl  a			; times 2 because of word
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		lda  #0
		sta  c64.ESTACK_LO+1,x
		rts
+		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_max_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  c64.SCRATCH_ZPB1
-		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  c64.SCRATCH_ZPB1
		bcc  +
		sta  c64.SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  c64.SCRATCH_ZPB1
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend

func_max_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #-128
		sta  c64.SCRATCH_ZPB1
-		lda  (c64.SCRATCH_ZPWORD1),y
		sec
		sbc  c64.SCRATCH_ZPB1
		bvc  +
		eor  #$80
+		bmi  +
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  c64.SCRATCH_ZPB1
		sta  c64.ESTACK_LO,x
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
		lda  (c64.SCRATCH_ZPWORD1),y
		dey
		cmp  _result_maxuw+1
		bcc  _lesseq
		bne  _greater
		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  _result_maxuw
		bcc  _lesseq
_greater	lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_maxuw
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_maxuw+1
		dey
_lesseq		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_maxuw
		sta  c64.ESTACK_LO,x
		lda  _result_maxuw+1
		sta  c64.ESTACK_HI,x
		dex
		rts
_result_maxuw	.word  0
		.pend

func_max_w	.proc
		lda  #$00
		sta  _result_maxw
		lda  #$80
		sta  _result_maxw+1
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
_loop
		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  _result_maxw
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		dey
		sbc  _result_maxw+1
		bvc  +
		eor  #$80
+		bmi  _lesseq
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_maxw
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_maxw+1
		dey
_lesseq		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_maxw
		sta  c64.ESTACK_LO,x
		lda  _result_maxw+1
		sta  c64.ESTACK_HI,x
		dex
		rts
_result_maxw	.word  0
		.pend


func_sum_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  c64.ESTACK_LO,x
		sta  c64.ESTACK_HI,x
_loop		lda  (c64.SCRATCH_ZPWORD1),y
		pha
		clc
		adc  c64.ESTACK_LO,x
		sta  c64.ESTACK_LO,x
		; sign extend the high byte
		pla
		and  #$80
		beq  +
		lda  #$ff
+		adc  c64.ESTACK_HI,x
		sta  c64.ESTACK_HI,x
		dey
		cpy  #255
		bne  _loop
		dex
		rts
		.pend

func_sum_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  c64.ESTACK_LO,x
		sta  c64.ESTACK_HI,x
-		lda  (c64.SCRATCH_ZPWORD1),y
		clc
		adc  c64.ESTACK_LO,x
		sta  c64.ESTACK_LO,x
		bcc  +
		inc  c64.ESTACK_HI,x
+		dey
		cpy  #255
		bne  -
		dex
		rts
		.pend

func_sum_uw	.proc
		jsr  pop_array_and_lengthmin1Y
		tya
		asl  a
		tay
		lda  #0
		sta  c64.ESTACK_LO,x
		sta  c64.ESTACK_HI,x
-		lda  (c64.SCRATCH_ZPWORD1),y
		iny
		clc
		adc  c64.ESTACK_LO,x
		sta  c64.ESTACK_LO,x
		lda  (c64.SCRATCH_ZPWORD1),y
		adc  c64.ESTACK_HI,x
		sta  c64.ESTACK_HI,x
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
		ldy  c64.ESTACK_LO,x
		dey				; length minus 1, for iteration
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		inx
		rts
		.pend

func_min_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #255
		sta  c64.SCRATCH_ZPB1
-		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  c64.SCRATCH_ZPB1
		bcs  +
		sta  c64.SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  c64.SCRATCH_ZPB1
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend


func_min_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #127
		sta  c64.SCRATCH_ZPB1
-		lda  (c64.SCRATCH_ZPWORD1),y
		clc
		sbc  c64.SCRATCH_ZPB1
		bvc  +
		eor  #$80
+		bpl  +
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  c64.SCRATCH_ZPB1
		sta  c64.ESTACK_LO,x
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
		lda  (c64.SCRATCH_ZPWORD1),y
		dey
		cmp   _result_minuw+1
		bcc  _less
		bne  _gtequ
		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  _result_minuw
		bcs  _gtequ
_less		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_minuw
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_minuw+1
		dey
_gtequ		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_minuw
		sta  c64.ESTACK_LO,x
		lda  _result_minuw+1
		sta  c64.ESTACK_HI,x
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
		lda  (c64.SCRATCH_ZPWORD1),y
		cmp   _result_minw
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		dey
		sbc  _result_minw+1
		bvc  +
		eor  #$80
+		bpl  _gtequ
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_minw
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  _result_minw+1
		dey
_gtequ		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_minw
		sta  c64.ESTACK_LO,x
		lda  _result_minw+1
		sta  c64.ESTACK_HI,x
		dex
		rts
_result_minw	.word  0
		.pend


func_len_str	.proc
	; -- push length of 0-terminated string on stack
		jsr  peek_address
		ldy  #0
-		lda  (c64.SCRATCH_ZPWORD1),y
		beq  +
		iny
		bne  -
+		tya
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_len_strp	.proc
	; -- push length of pascal-string on stack
		jsr  peek_address
		ldy  #0
		lda  (c64.SCRATCH_ZPWORD1),y	; first byte is length
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_rnd	.proc
	; -- put a random ubyte on the estack
		jsr  math.randbyte
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend

func_rndw	.proc
	; -- put a random uword on the estack
		jsr  math.randword
		sta  c64.ESTACK_LO,x
		tya
		sta  c64.ESTACK_HI,x
		dex
		rts
		.pend


func_memcopy	.proc		
	; note: clobbers A,Y
		inx
		stx  c64.SCRATCH_ZPREGX
		lda  c64.ESTACK_LO+2,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+2,x
		sta  c64.SCRATCH_ZPWORD1+1
		lda  c64.ESTACK_LO+1,x
		ldy  c64.ESTACK_HI+1,x
		pha
		lda  c64.ESTACK_LO,x
		tax
		pla
		jsr  c64utils.memcopy
		ldx  c64.SCRATCH_ZPREGX
		inx
		inx
		rts
		.pend
