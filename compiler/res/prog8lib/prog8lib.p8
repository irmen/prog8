; Prog8 internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ prog8_lib {

	%asm {{

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
		tay			; times 2 because of word array
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
		tay			; times 2 because of word array
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
		tay			; times 2 because of word array
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
		tay			; times 2 because of word array
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


func_memcopy	.proc		; clobbers A,Y
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

	}}
}


~ math {

;  some more interesting routines can be found here:
;	http://6502org.wikidot.com/software-math
;	http://codebase64.org/doku.php?id=base:6502_6510_maths
;


asmsub  multiply_bytes  (ubyte byte1 @ A, ubyte byte2 @ Y) -> clobbers() -> (ubyte @ A)  {
	; ---- multiply 2 bytes, result as byte in A  (signed or unsigned)
	%asm {{
		sta  c64.SCRATCH_ZPB1         ; num1
		sty  c64.SCRATCH_ZPREG        ; num2
		lda  #0
		beq  _enterloop
_doAdd		clc
                adc  c64.SCRATCH_ZPB1
_loop           asl  c64.SCRATCH_ZPB1
_enterloop      lsr  c64.SCRATCH_ZPREG
                bcs  _doAdd
                bne  _loop
		rts
	}}
}


asmsub  multiply_bytes_16  (ubyte byte1 @ A, ubyte byte2 @ Y) -> clobbers(X) -> (uword @ AY)  {
	; ---- multiply 2 bytes, result as word in A/Y (unsigned)
	%asm {{
                sta  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		lda  #0
		ldx  #8
		lsr  c64.SCRATCH_ZPB1
-               bcc  +
		clc
		adc  c64.SCRATCH_ZPREG
+               ror  a
		ror  c64.SCRATCH_ZPB1
		dex
		bne  -
		tay
		lda  c64.SCRATCH_ZPB1
		rts
	}}
}

asmsub  multiply_words  (uword number @ AY) -> clobbers(A,X) -> ()  {
	; ---- multiply two 16-bit words into a 32-bit result  (signed and unsigned)
	;      input: A/Y = first 16-bit number, c64.SCRATCH_ZPWORD1 in ZP = second 16-bit number
	;      output: multiply_words.result  4-bytes/32-bits product, LSB order (low-to-high)

	%asm {{
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1

mult16		lda  #$00
		sta  multiply_words_result+2	; clear upper bits of product
		sta  multiply_words_result+3
		ldx  #16			; for all 16 bits...
-	 	lsr  c64.SCRATCH_ZPWORD1+1	; divide multiplier by 2
		ror  c64.SCRATCH_ZPWORD1
		bcc  +
		lda  multiply_words_result+2	; get upper half of product and add multiplicand
		clc
		adc  c64.SCRATCH_ZPWORD2
		sta  multiply_words_result+2
		lda  multiply_words_result+3
		adc  c64.SCRATCH_ZPWORD2+1
+ 		ror  a				; rotate partial product
		sta  multiply_words_result+3
		ror  multiply_words_result+2
		ror  multiply_words_result+1
		ror  multiply_words_result
		dex
		bne  -
		rts

multiply_words_result	.byte  0,0,0,0

	}}
}

asmsub  divmod_ub  (ubyte number @ A, ubyte divisor @ Y) -> clobbers() -> (ubyte @ Y, ubyte @ A)  {
	; ---- divide A by Y, result quotient in Y, remainder in A   (unsigned)
	;      division by zero will result in quotient = 255 and remainder = original number
	%asm {{
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
	}}
}

asmsub  divmod_uw_asm  (uword divisor @ AY) -> clobbers() -> (uword @ AY)  {
	; ---- divide two unsigned words (16 bit each) into 16 bit results
	;      input:  c64.SCRATCH_ZPWORD1 in ZP: 16 bit number, A/Y: 16 bit divisor
	;      output: c64.SCRATCH_ZPWORD2 in ZP: 16 bit remainder, A/Y: 16 bit division result
	;      division by zero will result in quotient = 65535 and remainder = divident

	%asm {{

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
	}}
}

asmsub  randseed  (uword seed @ AY) -> clobbers(A, Y) -> ()  {
	; ---- reset the random seeds for the byte and word random generators
	;      default starting values are:  A=$2c Y=$9e
	%asm {{
		sta  randword._seed
		sty  randword._seed+1
		stx  c64.SCRATCH_ZPREG
		clc
		adc  #14
		sta  randbyte._seed
		ora  #$80		; make negative
		; jsr  c64flt.FREADSA
		; jsr  c64flt.RND		; reseed the float rng using the (negative) number in A
		ldx  c64.SCRATCH_ZPREG
		rts
	}}
}


asmsub  randbyte  () -> clobbers() -> (ubyte @ A)  {
	; ---- 8-bit pseudo random number generator into A

	%asm {{
		lda  _seed
		beq  +
		asl  a
		beq  ++		;if the input was $80, skip the EOR
		bcc  ++
+		eor  _magic	; #$1d		; could be self-modifying code to set new magic
+		sta  _seed
		rts

_seed		.byte  $3a
_magic		.byte  $1d
_magiceors	.byte  $1d, $2b, $2d, $4d, $5f, $63, $65, $69
		.byte  $71, $87, $8d, $a9, $c3, $cf, $e7, $f5

	}}
}

asmsub  randword  () -> clobbers() -> (uword @ AY)  {
	; ---- 16 bit pseudo random number generator into AY

	%asm {{
		lda  _seed
		beq  _lowZero	; $0000 and $8000 are special values to test for

 		; Do a normal shift
		asl  _seed
		lda  _seed+1
		rol  a
		bcc  _noEor

_doEor		; high byte is in A
		eor  _magic+1	; #>magic	; could be self-modifying code to set new magic
  		sta  _seed+1
  		lda  _seed
  		eor  _magic	; #<magic	; could be self-modifying code to set new magic
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
_magic		.word   $3f1d
_magiceors	.word   $3f1d, $3f81, $3fa5, $3fc5, $4075, $409d, $40cd, $4109
 		.word   $413f, $414b, $4153, $4159, $4193, $4199, $41af, $41bb

	}}
}


%asm {{

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
		; W + W*2
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		lda  c64.ESTACK_LO+1,x
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		adc  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend
		

mul_byte_5	.proc
		; X + X*4
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_5	.proc
		; W + W*4
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		lda  c64.ESTACK_LO+1,x
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		adc  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

		
mul_byte_6	.proc
		; X*2 + X*4
		lda  c64.ESTACK_LO+1,x
		asl  a
		sta  c64.SCRATCH_ZPREG
		asl  a
		clc
		adc  c64.SCRATCH_ZPREG
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_6	.proc
		; W*2 + W*4
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD1
		sta  c64.SCRATCH_ZPWORD2
		lda  c64.SCRATCH_ZPWORD1+1
		sta  c64.SCRATCH_ZPWORD2+1
		asl  c64.SCRATCH_ZPWORD2
		rol  c64.SCRATCH_ZPWORD2+1
		clc
		lda  c64.SCRATCH_ZPWORD1
		adc  c64.SCRATCH_ZPWORD2
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		adc  c64.SCRATCH_ZPWORD2+1
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
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD1
		sec
		lda  c64.SCRATCH_ZPWORD1
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		sbc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_9	.proc
		; X + X*8
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
		; W + W*8
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		lda  c64.ESTACK_LO+1,x
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		adc  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_10	.proc
		; X + X + X*8
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO+1,x
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_10	.proc
		; W*2 + W*8
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD1
		sta  c64.SCRATCH_ZPWORD2
		lda  c64.SCRATCH_ZPWORD1+1
		sta  c64.SCRATCH_ZPWORD2+1
		asl  c64.SCRATCH_ZPWORD2
		rol  c64.SCRATCH_ZPWORD2+1
		asl  c64.SCRATCH_ZPWORD2
		rol  c64.SCRATCH_ZPWORD2+1
		clc
		lda  c64.SCRATCH_ZPWORD1
		adc  c64.SCRATCH_ZPWORD2
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		adc  c64.SCRATCH_ZPWORD2+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend
		
mul_byte_11	.proc
		; X + X + X + X*8
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPREG
		asl  a
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPREG
		clc
		adc  c64.SCRATCH_ZPREG
		clc
		adc  c64.SCRATCH_ZPREG
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

; mul_word_11 is skipped (too much code)

mul_byte_12	.proc
		; X*4 + X*8
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		sta  c64.SCRATCH_ZPREG
		asl  a
		clc
		adc  c64.SCRATCH_ZPREG
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_12	.proc
		; W*4 + W*8
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD1
		sta  c64.SCRATCH_ZPWORD2
		lda  c64.SCRATCH_ZPWORD1+1
		sta  c64.SCRATCH_ZPWORD2+1
		asl  c64.SCRATCH_ZPWORD2
		rol  c64.SCRATCH_ZPWORD2+1
		clc
		lda  c64.SCRATCH_ZPWORD1
		adc  c64.SCRATCH_ZPWORD2
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		adc  c64.SCRATCH_ZPWORD2+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_13	.proc
		; X*16 - X -X -X
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPREG
		asl  a
		asl  a
		asl  a
		asl  a
		sec
		sbc  c64.SCRATCH_ZPREG
		sec
		sbc  c64.SCRATCH_ZPREG
		sec
		sbc  c64.SCRATCH_ZPREG
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

; mul_word_13 is skipped (too much code)

mul_byte_14	.proc
		; X*16 - X -X
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		asl  a
		sec
		sbc  c64.ESTACK_LO+1,x
		sec
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend
		
; mul_word_14 is skipped (too much code)

mul_byte_15	.proc
		; X*16 - X
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPREG
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
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		sec
		lda  c64.SCRATCH_ZPWORD1
		sbc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		sbc  c64.ESTACK_HI+1,x
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_20	.proc
		; X*4 + X*16
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		sta  c64.SCRATCH_ZPREG
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPREG
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_20	.proc
		; W*4 + W*16
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD1
		sta  c64.SCRATCH_ZPWORD2
		lda  c64.SCRATCH_ZPWORD1+1
		sta  c64.SCRATCH_ZPWORD2+1
		asl  c64.SCRATCH_ZPWORD2
		rol  c64.SCRATCH_ZPWORD2+1
		asl  c64.SCRATCH_ZPWORD2
		rol  c64.SCRATCH_ZPWORD2+1
		clc
		lda  c64.SCRATCH_ZPWORD1
		adc  c64.SCRATCH_ZPWORD2
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		adc  c64.SCRATCH_ZPWORD2+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend

mul_byte_25	.proc
		; X + X*8 + X*16
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		sta  c64.SCRATCH_ZPREG
		asl  a
		clc
		adc  c64.SCRATCH_ZPREG
		clc
		adc  c64.ESTACK_LO+1,x
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

mul_word_25	.proc
		; W + W*8 + W*16
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		lda  c64.ESTACK_LO+1,x
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		adc  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		lda  c64.ESTACK_LO+1,x
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		adc  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend		

mul_byte_40	.proc
		; X*8 + X*32
		lda  c64.ESTACK_LO+1,x
		asl  a
		asl  a
		asl  a
		sta  c64.SCRATCH_ZPREG
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPREG
		sta  c64.ESTACK_LO+1,x
		rts
		.pend
		
mul_word_40	.proc
		; W*8 + W*32
		lda  c64.ESTACK_LO+1,x
		sta  c64.SCRATCH_ZPWORD1
		lda  c64.ESTACK_HI+1,x
		sta  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		asl  c64.SCRATCH_ZPWORD1
		rol  c64.SCRATCH_ZPWORD1+1
		clc
		lda  c64.ESTACK_LO+1,x
		adc  c64.SCRATCH_ZPWORD1
		sta  c64.ESTACK_LO+1,x
		lda  c64.ESTACK_HI+1,x
		adc  c64.SCRATCH_ZPWORD1+1
		sta  c64.ESTACK_HI+1,x
		rts
		.pend
		

}}

}
