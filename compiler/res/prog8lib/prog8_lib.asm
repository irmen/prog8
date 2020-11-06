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



memcopy16_up	.proc
	; -- copy memory UP from (P8ZP_SCRATCH_W1) to (P8ZP_SCRATCH_W2) of length X/Y (16-bit, X=lo, Y=hi)
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
	; -- fill memory from (P8ZP_SCRATCH_W1), length XY, with value in A.
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
	; -- fill memory from (P8ZP_SCRATCH_W1) number of words in P8ZP_SCRATCH_W2, with word value in AY.
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
		inc $d020
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		bne  -
		rts
		.pend

strcmp_expression	.proc
		; TODO expression call args not via stack
		inx
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		inx
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		jmp  strcmp_mem
		.pend


strcmp_mem	.proc
		; --   compares strings in s1 (AY) and s2 (P8ZP_SCRATCH_W2).
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
