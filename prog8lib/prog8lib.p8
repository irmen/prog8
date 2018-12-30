; Prog8 internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ prog8_lib {
		; @TODO move all this assembly to a real .asm file instead and include that...


		; note: the following ZP scratch registers must be the same as in c64lib
		memory  ubyte  SCRATCH_ZPB1	= $02		; scratch byte 1 in ZP
		memory  ubyte  SCRATCH_ZPREG	= $03		; scratch register in ZP
		memory  ubyte  SCRATCH_ZPREGX	= $fa		; temp storage for X register (stack pointer)
		memory  uword  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  uword  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)
		const   uword  ESTACK_LO	= $ce00
		const   uword  ESTACK_HI	= $cf00


	%asm {{

; 16-bit rotate right (as opposed to the 6502's usual 17-bit rotate with carry)
; the word is placed in SCRATCH_ZPWORD1
ror2_word	.proc
		lsr  SCRATCH_ZPWORD1+1
		ror  SCRATCH_ZPWORD1
		bcc  +
		lda  SCRATCH_ZPWORD1+1
		ora  #$80
		sta  SCRATCH_ZPWORD1+1
+		rts
		.pend



; @todo:  implement stubs!
; @todo:  move float operations to their own library (only included when floats are enabled)

ub2float	.proc
		; -- convert ubyte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  SCRATCH_ZPREGX
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD1+1
		ldy  SCRATCH_ZPB1
		jsr  c64.FREADUY
_fac_to_mem	ldx  SCRATCH_ZPWORD2
		ldy  SCRATCH_ZPWORD2+1
		jsr  c64.MOVMF
		ldx  SCRATCH_ZPREGX
		rts
		.pend

b2float		.proc
		; -- convert byte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  SCRATCH_ZPREGX
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		lda  SCRATCH_ZPB1
		jsr  c64.FREADSA
		jmp  ub2float._fac_to_mem
		.pend

uw2float	.proc
		; -- convert uword in SCRATCH_ZPWORD1 to float at address A/Y
		stx  SCRATCH_ZPREGX
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64flt.GIVUAYFAY
		jmp  ub2float._fac_to_mem
		.pend

w2float		.proc
		; -- convert word in SCRATCH_ZPWORD1 to float at address A/Y
		stx  SCRATCH_ZPREGX
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		ldy  SCRATCH_ZPWORD1
		lda  SCRATCH_ZPWORD1+1
		jsr  c64.GIVAYF
		jmp  ub2float._fac_to_mem
		.pend
		
stack_b2float	.proc
		; -- b2float operating on the stack
		inx
		lda  ESTACK_LO,x
		stx  SCRATCH_ZPREGX
		jsr  c64.FREADSA
		jmp  push_fac1_as_result
		.pend
		
stack_w2float	.proc
		; -- w2float operating on the stack
		inx
		ldy  ESTACK_LO,x
		lda  ESTACK_HI,x
		stx  SCRATCH_ZPREGX
		jsr  c64.GIVAYF
		jmp  push_fac1_as_result
		.pend

stack_ub2float	.proc
		; -- ub2float operating on the stack
		inx
		lda  ESTACK_LO,x
		stx  SCRATCH_ZPREGX
		tay
		jsr  c64.FREADUY
		jmp  push_fac1_as_result
		.pend

stack_uw2float	.proc
		; -- uw2float operating on the stack
		inx
		lda  ESTACK_LO,x
		ldy  ESTACK_HI,x
		stx  SCRATCH_ZPREGX
		jsr  c64flt.GIVUAYFAY
		jmp  push_fac1_as_result
		.pend
		
stack_float2w	.proc	
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.AYINT
		ldx  SCRATCH_ZPREGX
		lda  $64
		sta  ESTACK_HI,x
		lda  $65
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		
stack_float2uw	.proc	
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.GETADR
		ldx  SCRATCH_ZPREGX
		sta  ESTACK_HI,x
		tya
		sta  ESTACK_LO,x
		dex
		rts
		.pend

push_float	.proc
		; ---- push mflpt5 in A/Y onto stack 
		; (taking 3 stack positions = 6 bytes of which 1 is padding)
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		ldy  #0
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_LO,x
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_HI,x
		dex
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_LO,x
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_HI,x
		dex
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		

add_a_to_zpword	.proc
		; -- add ubyte in A to the uword in SCRATCH_ZPWORD1
		clc
		adc  SCRATCH_ZPWORD1
		sta  SCRATCH_ZPWORD1
		bcc  +
		inc  SCRATCH_ZPWORD1+1
+		rts
		.pend

pop_index_times_5	.proc
		inx
		lda  ESTACK_LO,x
		sta  SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  SCRATCH_ZPB1	; A*=5
		rts
		.pend
		
push_float_from_indexed_var	.proc
		; -- push the float from the array at A/Y with index on stack, onto the stack.
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		jsr  pop_index_times_5
		jsr  add_a_to_zpword
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jmp  push_float
		.pend

pop_float	.proc
		; ---- pops mflpt5 from stack to memory A/Y
		; (frees 3 stack positions = 6 bytes of which 1 is padding)
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		ldy  #4
		inx
		lda  ESTACK_LO,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		inx
		lda  ESTACK_HI,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		lda  ESTACK_LO,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		inx
		lda  ESTACK_HI,x
		sta  (SCRATCH_ZPWORD1),y
		dey
		lda  ESTACK_LO,x
		sta  (SCRATCH_ZPWORD1),y
		rts
		.pend
		
pop_float_fac1	.proc
		; -- pops float from stack into FAC1
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jmp  c64.MOVFM
		.pend
		
pop_float_to_indexed_var	.proc
		; -- pop the float on the stack, to the memory in the array at A/Y indexed by the byte on stack
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		jsr  pop_index_times_5
		jsr  add_a_to_zpword
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jmp  pop_float
		.pend

copy_float	.proc
		; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1, 
		;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		ldy  #0
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  (SCRATCH_ZPWORD2),y
		rts
		.pend

inc_var_f	.proc
		; -- add 1 to float pointed to by A/Y
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		stx  SCRATCH_ZPREGX
		jsr  c64.MOVFM
		lda  #<c64.FL_FONE
		ldy  #>c64.FL_FONE
		jsr  c64.FADD
		ldx  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64.MOVMF
		ldx  SCRATCH_ZPREGX
		rts
		.pend
                
dec_var_f	.proc
		; -- subtract 1 from float pointed to by A/Y
		sta  SCRATCH_ZPWORD1
		sty  SCRATCH_ZPWORD1+1
		stx  SCRATCH_ZPREGX
		lda  #<c64.FL_FONE
		ldy  #>c64.FL_FONE
		jsr  c64.MOVFM
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64.FSUB
		ldx  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64.MOVMF
		ldx  SCRATCH_ZPREGX
		rts
		.pend

pop_2_floats_f2_in_fac1	.proc
		; -- pop 2 floats from stack, load the second one in FAC1 as well
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jmp  c64.MOVFM
		.pend
		
fmath_float1	.byte 0,0,0,0,0	; storage for a mflpt5 value
fmath_float2	.byte 0,0,0,0,0	; storage for a mflpt5 value

push_fac1_as_result	.proc
		; -- push the float in FAC1 onto the stack, and return from calculation
		ldx  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.MOVMF
		lda  #<fmath_float1
		ldy  #>fmath_float1
		ldx  SCRATCH_ZPREGX
		jmp  push_float
		.pend
		

floordiv_f	.proc
		; -- push f1//f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.FDIV
		jsr  c64.INT
		jmp  push_fac1_as_result
		.pend

div_f		.proc
		; -- push f1/f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.FDIV
		jmp  push_fac1_as_result
		.pend

add_f		.proc
		; -- push f1+f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.FADD
		jmp  push_fac1_as_result
		.pend

sub_f		.proc
		; -- push f1-f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.FSUB
		jmp  push_fac1_as_result
		.pend

mul_f		.proc
		; -- push f1*f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.FMULT
		jmp  push_fac1_as_result
		.pend

neg_b		.proc
		lda  ESTACK_LO+1,x
		eor  #255
		clc
		adc  #1
		sta  ESTACK_LO+1,x
		rts
		.pend
                
neg_w		.proc
		sec
		lda  #0
		sbc  ESTACK_LO+1,x
		sta  ESTACK_LO+1,x
		lda  #0
		sbc  ESTACK_HI+1,x
		sta  ESTACK_HI+1,x
		rts
		.pend
		
neg_f		.proc
		; -- push -flt back on stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.NEGOP
		jmp  push_fac1_as_result
		.pend
		
inv_word	.proc
		lda  ESTACK_LO+1,x
		eor  #255
		sta  ESTACK_LO+1,x
		lda  ESTACK_HI+1,x
		eor  #255
		sta  ESTACK_HI+1,x
		rts
		.pend

not_byte	.proc
		lda  ESTACK_LO+1,x
		beq  +
		lda  #0
		beq ++
+		lda  #1
+		sta  ESTACK_LO+1,x
		rts
		.pend
		
not_word	.proc
		lda  ESTACK_LO + 1,x
		ora  ESTACK_HI + 1,x
		beq  +
		lda  #0
		beq  ++
+		lda  #1
+		sta  ESTACK_LO + 1,x
		sta  ESTACK_HI + 1,x
		rts
		.pend
		
abs_b		.proc
		; -- push abs(byte) on stack (as byte)
		lda  ESTACK_LO+1,x
		bmi  neg_b
		rts
		.pend
		
abs_w		.proc
		; -- push abs(word) on stack (as word)
		lda  ESTACK_HI+1,x
		bmi  neg_w
		rts
		.pend

abs_f		.proc
		; -- push abs(float) on stack (as float)
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.ABS
		jmp  push_fac1_as_result
		.pend

add_w		.proc
		; -- push word+word / uword+uword
		inx
		clc
		lda  ESTACK_LO,x
		adc  ESTACK_LO+1,x
		sta  ESTACK_LO+1,x
		lda  ESTACK_HI,x
		adc  ESTACK_HI+1,x
		sta  ESTACK_HI+1,x
		rts
		.pend
		
sub_w		.proc
		; -- push word-word
		inx
		sec
		lda  ESTACK_LO+1,x
		sbc  ESTACK_LO,x
		sta  ESTACK_LO+1,x
		lda  ESTACK_HI+1,x
		sbc  ESTACK_HI,x
		sta  ESTACK_HI+1,x
		rts
		.pend

mul_byte	.proc
		; -- b*b->b (signed and unsigned)
		inx
		lda  ESTACK_LO,x
		ldy  ESTACK_LO+1,x
		jsr  math.multiply_bytes
		sta  ESTACK_LO+1,x
		rts
		.pend
		
mul_word	.proc
		inx
		lda  ESTACK_LO,x
		sta  SCRATCH_ZPWORD1
		lda  ESTACK_HI,x
		sta  SCRATCH_ZPWORD1+1
		lda  ESTACK_LO+1,x
		ldy  ESTACK_HI+1,x
		stx  SCRATCH_ZPREGX
		jsr  math.multiply_words
		ldx  SCRATCH_ZPREGX
		lda  math.multiply_words_result
		sta  ESTACK_LO+1,x
		lda  math.multiply_words_result+1
		sta  ESTACK_HI+1,x
		rts
		.pend
		
div_b		.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "div_b not implemented"
		.pend
		
div_ub		.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "div_ub not implemented"
		.pend
		
div_w		.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "div_w not implemented"
		.pend
		
div_uw		.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "div_uw not implemented"
		.pend

remainder_b	.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "remainder_b via div_b?"
		.pend
		
remainder_ub	.proc
		inx
		lda  ESTACK_LO,x	; right operand
		sta  SCRATCH_ZPB1
		lda  ESTACK_LO+1,x	; left operand
-		cmp  SCRATCH_ZPB1
		bcc  +
		sbc  SCRATCH_ZPB1
		jmp  -
+		sta  ESTACK_LO+1,x
		rts
		.pend
		
remainder_w	.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "remainder_w not implemented - via div_w"
		.pend
		
remainder_uw	.proc
		inx
		lda #42
		sta ESTACK_LO+1,x
		lda #0
		sta ESTACK_HI+1,x
		rts
		.warn "remainder_uw not implemented - via div_uw"
		.pend
		
equal_w		.proc
		; -- are the two words on the stack identical?
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		bne  equal_b._equal_b_false
		lda  ESTACK_HI+1,x
		cmp  ESTACK_HI+2,x
		bne  equal_b._equal_b_false
		beq  equal_b._equal_b_true
		.pend

notequal_b	.proc
		; -- are the two bytes on the stack different?
		inx
		lda  ESTACK_LO,x
		eor  ESTACK_LO+1,x
		sta  ESTACK_LO+1,x
		rts
		.pend
		
notequal_w	.proc
		; -- are the two words on the stack different?
		inx
		lda  ESTACK_LO,x
		eor  ESTACK_LO+1,x
		beq  +
		sta  ESTACK_LO+1,x
		rts
+		lda  ESTACK_HI,x
		eor  ESTACK_HI+1,x
		sta  ESTACK_LO+1,x
		rts
		.pend
		
less_ub		.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend
	
less_b		.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		sec
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

less_uw		.proc
		lda  ESTACK_HI+2,x
		cmp  ESTACK_HI+1,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

less_w		.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		lda  ESTACK_HI+2,x
		sbc  ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

equal_b		.proc
		; -- are the two bytes on the stack identical?
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bne  _equal_b_false
_equal_b_true	lda  #1
_equal_b_store	inx
		sta  ESTACK_LO+1,x
		rts
_equal_b_false	lda  #0
		beq  _equal_b_store
		.pend

lesseq_ub	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcc  equal_b._equal_b_true
		beq  equal_b._equal_b_true		; @todo optimize by flipping comparison
		bcs  equal_b._equal_b_false
		.pend
	
lesseq_b	.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		clc
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend

lesseq_uw	.proc
		lda  ESTACK_HI+1,x
		cmp  ESTACK_HI+2,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend
		
lesseq_w	.proc
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		lda  ESTACK_HI+1,x
		sbc  ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_ub	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		beq  equal_b._equal_b_false
		bcs  equal_b._equal_b_true		; @todo optimize by flipping comparison?
		bcc  equal_b._equal_b_false
		.pend
	
greater_b	.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		clc
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greater_uw	.proc
		lda  ESTACK_HI+1,x
		cmp  ESTACK_HI+2,x
		bcc  equal_b._equal_b_true
		bne  equal_b._equal_b_false
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		bcc  equal_b._equal_b_true
		bcs  equal_b._equal_b_false
		.pend

greater_w	.proc
		lda  ESTACK_LO+1,x
		cmp  ESTACK_LO+2,x
		lda  ESTACK_HI+1,x
		sbc  ESTACK_HI+2,x
		bvc  +
		eor  #$80
+		bmi  equal_b._equal_b_true
		bpl  equal_b._equal_b_false
		.pend
	
greatereq_ub	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend
	
greatereq_b	.proc
		; see http://www.6502.org/tutorials/compare_beyond.html
		lda  ESTACK_LO+2,x
		sec
		sbc  ESTACK_LO+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

greatereq_uw	.proc
		lda  ESTACK_HI+2,x
		cmp  ESTACK_HI+1,x
		bcc  equal_b._equal_b_false
		bne  equal_b._equal_b_true
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		bcs  equal_b._equal_b_true
		bcc  equal_b._equal_b_false
		.pend

greatereq_w	.proc
		lda  ESTACK_LO+2,x
		cmp  ESTACK_LO+1,x
		lda  ESTACK_HI+2,x
		sbc  ESTACK_HI+1,x
		bvc  +
		eor  #$80
+		bpl  equal_b._equal_b_true
		bmi  equal_b._equal_b_false
		.pend

equal_f		.proc
		; -- are the two mflpt5 numbers on the stack identical?
		inx
		inx
		inx
		inx
		lda  ESTACK_LO-3,x
		cmp  ESTACK_LO,x
		bne  equal_b._equal_b_false
		lda  ESTACK_LO-2,x
		cmp  ESTACK_LO+1,x
		bne  equal_b._equal_b_false
		lda  ESTACK_LO-1,x
		cmp  ESTACK_LO+2,x
		bne  equal_b._equal_b_false
		lda  ESTACK_HI-2,x
		cmp  ESTACK_HI+1,x
		bne  equal_b._equal_b_false
		lda  ESTACK_HI-1,x
		cmp  ESTACK_HI+2,x
		bne  equal_b._equal_b_false
		beq  equal_b._equal_b_true
		.pend

notequal_f	.proc
		; -- are the two mflpt5 numbers on the stack different?
		jsr  equal_f
		eor  #1		; invert the result
		sta  ESTACK_LO+1,x
		rts
		.pend

less_f		.proc
		; -- is f1 < f2?
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend
		

lesseq_f	.proc
		; -- is f1 <= f2?
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greater_f	.proc
		; -- is f1 > f2?
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greatereq_f	.proc
		; -- is f1 >= f2?
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

compare_floats	.proc
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.MOVFM		; fac1 = flt1
		lda  #<fmath_float2
		ldy  #>fmath_float2
		stx  SCRATCH_ZPREG
		jsr  c64.FCOMP		; A = flt1 compared with flt2 (0=equal, 1=flt1>flt2, 255=flt1<flt2)
		ldx  SCRATCH_ZPREG
		rts
_return_false	lda  #0
_return_result  sta  ESTACK_LO,x
		dex
		rts
_return_true	lda  #1
		bne  _return_result
		.pend		

func_sin	.proc
		; -- push sin(f) back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.SIN
		jmp  push_fac1_as_result
		.pend

func_cos	.proc
		; -- push cos(f) back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.COS
		jmp  push_fac1_as_result
		.pend
		
func_tan	.proc
		; -- push tan(f) back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.TAN
		jmp  push_fac1_as_result
		.pend
		
func_atan	.proc
		; -- push atan(f) back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.ATN
		jmp  push_fac1_as_result
		.pend
		
func_ln		.proc
		; -- push ln(f) back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.LOG
		jmp  push_fac1_as_result
		.pend
		
func_log2	.proc
		; -- push log base 2, ln(f)/ln(2), back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.LOG
		jsr  c64.MOVEF
		lda  #<c64.FL_LOG2
		ldy  #>c64.FL_LOG2
		jsr  c64.MOVFM
		jsr  c64.FDIVT
		jmp  push_fac1_as_result
		.pend
		
func_sqrt	.proc
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.SQR
		jmp  push_fac1_as_result
		.pend
		
func_rad	.proc
		; -- convert degrees to radians (d * pi / 180)
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		lda  #<_pi_div_180
		ldy  #>_pi_div_180
		jsr  c64.FMULT
		jmp  push_fac1_as_result
_pi_div_180	.byte 123, 14, 250, 53, 18		; pi / 180
		.pend
		
func_deg	.proc
		; -- convert radians to degrees (d * (1/ pi * 180))
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		lda  #<_one_over_pi_div_180
		ldy  #>_one_over_pi_div_180
		jsr  c64.FMULT
		jmp  push_fac1_as_result
_one_over_pi_div_180	.byte 134, 101, 46, 224, 211		; 1 / (pi * 180)
		.pend
		
func_round	.proc
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.FADDH
		jsr  c64.INT
		jmp  push_fac1_as_result
		.pend
		
func_floor	.proc
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.INT
		jmp  push_fac1_as_result
		.pend
		
func_ceil	.proc
		; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.MOVMF
		jsr  c64.INT
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64.FCOMP
		cmp  #0
		beq  +
		lda  #<c64.FL_FONE
		ldy  #>c64.FL_FONE
		jsr  c64.FADD
+		jmp  push_fac1_as_result
		.pend
		
			
peek_address	.proc
		; -- peek address on stack into SCRATCH_ZPWORD1
		lda  ESTACK_LO+1,x
		sta  SCRATCH_ZPWORD1
		lda  ESTACK_HI+1,x
		sta  SCRATCH_ZPWORD1+1
		rts
		.pend

func_any_b	.proc
		inx
		lda  ESTACK_LO,x	; array size
_entry		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (SCRATCH_ZPWORD1),y
		bne  _got_any
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #0
		sta  ESTACK_LO+1,x
		rts
_got_any	lda  #1
		sta  ESTACK_LO+1,x
		rts
		.pend
		
func_any_w	.proc
		inx
		lda  ESTACK_LO,x	; array size
		asl  a			; times 2 because of word
		jmp  func_any_b._entry
		.pend

func_any_f	.proc
		inx
		lda  ESTACK_LO,x	; array size
		sta  SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  SCRATCH_ZPB1	; times 5 because of float
		jmp  func_any_b._entry
		.pend

func_all_b	.proc
		inx
		lda  ESTACK_LO,x	; array size
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (SCRATCH_ZPWORD1),y
		beq  _got_not_all
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  ESTACK_LO+1,x
		rts
_got_not_all	lda  #0
		sta  ESTACK_LO+1,x
		rts
		.pend
		
func_all_w	.proc
		inx
		lda  ESTACK_LO,x	; array size
		asl  a			; times 2 because of word
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (SCRATCH_ZPWORD1),y
		bne  +
		lda  #0
		sta  ESTACK_LO+1,x
		rts
+		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  ESTACK_LO+1,x
		rts
		.pend

func_all_f	.proc
		inx
		lda  ESTACK_LO,x	; array size
		sta  SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  SCRATCH_ZPB1	; times 5 because of float
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (SCRATCH_ZPWORD1),y
		bne  +
		lda  #0
		sta  ESTACK_LO+1,x
		rts
+		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  ESTACK_LO+1,x
		rts
		.pend
		
func_max_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  SCRATCH_ZPB1
-		lda  (SCRATCH_ZPWORD1),y
		cmp  SCRATCH_ZPB1
		bcc  +
		sta  SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  SCRATCH_ZPB1
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		
func_max_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #-128
		sta  SCRATCH_ZPB1
-		lda  (SCRATCH_ZPWORD1),y
		sec
		sbc  SCRATCH_ZPB1
		bvc  +
		eor  #$80
+		bmi  +		
		lda  (SCRATCH_ZPWORD1),y
		sta  SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  SCRATCH_ZPB1
		sta  ESTACK_LO,x
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
		lda  (SCRATCH_ZPWORD1),y
		dey
		cmp  _result_maxuw+1
		bcc  _lesseq
		bne  _greater
		lda  (SCRATCH_ZPWORD1),y
		cmp  _result_maxuw
		bcc  _lesseq
_greater	lda  (SCRATCH_ZPWORD1),y
		sta  _result_maxuw
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  _result_maxuw+1
		dey
_lesseq		dey
		dey
		bpl  _loop			; @todo doesn't work for arrays where y will be >127. FIX OTHER LOOPS TOO!
		lda  _result_maxuw
		sta  ESTACK_LO,x
		lda  _result_maxuw+1
		sta  ESTACK_HI,x
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
		lda  (SCRATCH_ZPWORD1),y
		cmp  _result_maxw
		iny
		lda  (SCRATCH_ZPWORD1),y
		dey
		sbc  _result_maxw+1
		bvc  +
		eor  #$80
+		bmi  _lesseq
		lda  (SCRATCH_ZPWORD1),y
		sta  _result_maxw
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  _result_maxw+1
		dey
_lesseq		dey
		dey
		bpl  _loop
		lda  _result_maxw
		sta  ESTACK_LO,x
		lda  _result_maxw+1
		sta  ESTACK_HI,x
		dex
		rts
_result_maxw	.word  0
		.pend
		
func_max_f	.proc
		lda  #<_min_float
		ldy  #>_min_float
		jsr  c64.MOVFM			; fac1=min(float)
		lda  #255
		sta  _cmp_mod+1			; compare using 255 so we keep larger values
_minmax_entry	jsr  pop_array_and_lengthmin1Y
		stx  SCRATCH_ZPREGX
-		sty  SCRATCH_ZPREG
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64.FCOMP
_cmp_mod	cmp  #255			; will be modified
		bne  +
		; fac1 is smaller/larger, so store the new value instead
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64.MOVFM
		ldy  SCRATCH_ZPREG
		dey
		cmp  #255
		beq  +
		lda  SCRATCH_ZPWORD1
		clc
		adc  #5
		sta  SCRATCH_ZPWORD1
		bcc  -
		inc  SCRATCH_ZPWORD1+1
		bne  -
+		jmp  push_fac1_as_result
_min_float	.byte  255,255,255,255,255	; -1.7014118345e+38
		.pend
		

func_sum_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  ESTACK_LO,x
		sta  ESTACK_HI,x
_loop		lda  (SCRATCH_ZPWORD1),y
		pha
		clc
		adc  ESTACK_LO,x
		sta  ESTACK_LO,x
		; sign extend the high byte
		pla
		and  #$80
		beq  +
		lda  #$ff
+		adc  ESTACK_HI,x
		sta  ESTACK_HI,x
		dey
		cpy  #255
		bne  _loop
		dex
		rts
		.pend
		
func_sum_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #0
		sta  ESTACK_LO,x
		sta  ESTACK_HI,x
-		lda  (SCRATCH_ZPWORD1),y
		clc
		adc  ESTACK_LO,x
		sta  ESTACK_LO,x
		bcc  +
		inc  ESTACK_HI,x
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
		sta  ESTACK_LO,x
		sta  ESTACK_HI,x
-		lda  (SCRATCH_ZPWORD1),y
		iny
		clc
		adc  ESTACK_LO,x
		sta  ESTACK_LO,x
		lda  (SCRATCH_ZPWORD1),y
		adc  ESTACK_HI,x
		sta  ESTACK_HI,x
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

func_sum_f	.proc
		lda  #<c64.FL_NEGHLF
		ldy  #>c64.FL_NEGHLF
		jsr  c64.MOVFM
		jsr  pop_array_and_lengthmin1Y
		stx  SCRATCH_ZPREGX
-		sty  SCRATCH_ZPREG
		lda  SCRATCH_ZPWORD1
		ldy  SCRATCH_ZPWORD1+1
		jsr  c64.FADD
		ldy  SCRATCH_ZPREG
		dey
		cpy  #255
		beq  +
		lda  SCRATCH_ZPWORD1
		clc
		adc  #5
		sta  SCRATCH_ZPWORD1
		bcc  -
		inc  SCRATCH_ZPWORD1+1
		bne  -
+		jsr  c64.FADDH
		jmp  push_fac1_as_result
		.pend
		
		
pop_array_and_lengthmin1Y	.proc
		inx
		ldy  ESTACK_LO,x
		dey				; length minus 1, for iteration
		lda  ESTACK_LO+1,x
		sta  SCRATCH_ZPWORD1
		lda  ESTACK_HI+1,x
		sta  SCRATCH_ZPWORD1+1
		inx
		rts
		.pend
		
func_min_ub	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #255
		sta  SCRATCH_ZPB1
-		lda  (SCRATCH_ZPWORD1),y
		cmp  SCRATCH_ZPB1
		bcs  +
		sta  SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  SCRATCH_ZPB1
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		
		
func_min_b	.proc
		jsr  pop_array_and_lengthmin1Y
		lda  #127
		sta  SCRATCH_ZPB1
-		lda  (SCRATCH_ZPWORD1),y
		clc
		sbc  SCRATCH_ZPB1
		bvc  +
		eor  #$80
+		bpl  +		
		lda  (SCRATCH_ZPWORD1),y
		sta  SCRATCH_ZPB1
+		dey
		cpy  #255
		bne  -
		lda  SCRATCH_ZPB1
		sta  ESTACK_LO,x
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
		lda  (SCRATCH_ZPWORD1),y
		dey
		cmp   _result_minuw+1
		bcc  _less
		bne  _gtequ
		lda  (SCRATCH_ZPWORD1),y
		cmp  _result_minuw
		bcs  _gtequ
_less		lda  (SCRATCH_ZPWORD1),y
		sta  _result_minuw
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  _result_minuw+1
		dey
_gtequ		dey
		dey
		bpl  _loop
		lda  _result_minuw
		sta  ESTACK_LO,x
		lda  _result_minuw+1
		sta  ESTACK_HI,x
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
		lda  (SCRATCH_ZPWORD1),y
		cmp   _result_minw
		iny
		lda  (SCRATCH_ZPWORD1),y
		dey
		sbc  _result_minw+1
		bvc  +
		eor  #$80
+		bpl  _gtequ
		lda  (SCRATCH_ZPWORD1),y
		sta  _result_minw
		iny
		lda  (SCRATCH_ZPWORD1),y
		sta  _result_minw+1
		dey
_gtequ		dey
		dey
		bpl  _loop
		lda  _result_minw
		sta  ESTACK_LO,x
		lda  _result_minw+1
		sta  ESTACK_HI,x
		dex
		rts
_result_minw	.word  0
		.pend
		
func_min_f	.proc
		lda  #<_max_float
		ldy  #>_max_float
		jsr  c64.MOVFM			; fac1=max(float)
		lda  #1
		sta  func_max_f._cmp_mod+1	; compare using 1 so we keep smaller values
		jmp  func_max_f._minmax_entry
_max_float	.byte  255,127,255,255,255	; 1.7014118345e+38
		.pend


func_len_str	.proc
		; -- push length of 0-terminated string on stack
		jsr  peek_address
		ldy  #0
-		lda  (SCRATCH_ZPWORD1),y
		beq  +
		iny
		bne  -
+		tya
		sta  ESTACK_LO+1,x
		rts
		.pend
                
func_len_strp	.proc
		; -- push length of pascal-string on stack
		jsr  peek_address
		ldy  #0
		lda  (SCRATCH_ZPWORD1),y	; first byte is length
		sta  ESTACK_LO+1,x
		rts
		.pend

func_rnd	.proc
		; -- put a random ubyte on the estack
		jsr  math.randbyte
		sta  ESTACK_LO,x
		dex
		rts
		.pend
		
func_rndw	.proc
		; -- put a random uword on the estack
		jsr  math.randword
		sta  ESTACK_LO,x
		tya
		sta  ESTACK_HI,x
		dex
		rts
		.pend

func_rndf	.proc
		; -- put a random floating point value on the stack
		stx  SCRATCH_ZPREG
		lda  #1
		jsr  c64.FREADSA
		jsr  c64.RND		; rng into fac1
		ldx  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jsr  c64.MOVMF	; fac1 to mem X/Y
		ldx  SCRATCH_ZPREG
		lda  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jmp  push_float
_rndf_rnum5	.byte  0,0,0,0,0
		.pend
    
	}}
}


~ math {

;  some more interesting routines can be found here:
;	http://6502org.wikidot.com/software-math
;	http://codebase64.org/doku.php?id=base:6502_6510_maths
;
		; note: the following ZP scratch registers must be the same as in c64lib
		memory  ubyte  SCRATCH_ZPB1	= $02		; scratch byte 1 in ZP
		memory  ubyte  SCRATCH_ZPREG	= $03		; scratch register in ZP
		memory  uword  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  uword  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)



asmsub  multiply_bytes  (ubyte byte1 @ A, ubyte byte2 @ Y) -> clobbers() -> (ubyte @ A)  {
	; ---- multiply 2 bytes, result as byte in A  (signed or unsigned)
	%asm {{
		sta  SCRATCH_ZPB1         ; num1
		sty  SCRATCH_ZPREG        ; num2
		lda  #0
		beq  _enterloop
_doAdd		clc
                adc  SCRATCH_ZPB1
_loop           asl  SCRATCH_ZPB1
_enterloop      lsr  SCRATCH_ZPREG
                bcs  _doAdd
                bne  _loop
		rts
	}}
}


asmsub  multiply_bytes_16  (ubyte byte1 @ A, ubyte byte2 @ Y) -> clobbers(X) -> (uword @ AY)  {
	; ---- multiply 2 bytes, result as word in A/Y (unsigned)
	%asm {{
                sta  SCRATCH_ZPB1
		sty  SCRATCH_ZPREG
		lda  #0
		ldx  #8
		lsr  SCRATCH_ZPB1
-               bcc  +
		clc
		adc  SCRATCH_ZPREG
+               ror  a
		ror  SCRATCH_ZPB1
		dex
		bne  -
		tay
		lda  SCRATCH_ZPB1
		rts
	}}
}

asmsub  multiply_words  (uword number @ AY) -> clobbers(A,X) -> ()  {
	; ---- multiply two 16-bit words into a 32-bit result  (signed and unsigned)
	;      input: A/Y = first 16-bit number, SCRATCH_ZPWORD1 in ZP = second 16-bit number
	;      output: multiply_words.result  4-bytes/32-bits product, LSB order (low-to-high)

	%asm {{
		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1

mult16		lda  #$00
		sta  multiply_words_result+2	; clear upper bits of product
		sta  multiply_words_result+3
		ldx  #16			; for all 16 bits...
-	 	lsr  SCRATCH_ZPWORD1+1		; divide multiplier by 2
		ror  SCRATCH_ZPWORD1
		bcc  +
		lda  multiply_words_result+2	; get upper half of product and add multiplicand
		clc
		adc  SCRATCH_ZPWORD2
		sta  multiply_words_result+2
		lda  multiply_words_result+3
		adc  SCRATCH_ZPWORD2+1
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

asmsub  divmod_bytes  (ubyte number @ X, ubyte divisor @ Y) -> clobbers() -> (ubyte @ X, ubyte @ A)  {
	; ---- divide X by Y, result quotient in X, remainder in A   (unsigned)
	;      division by zero will result in quotient = 255 and remainder = original number
	%asm {{
		stx  SCRATCH_ZPB1
		sty  SCRATCH_ZPREG

		lda  #0
		ldx  #8
		asl  SCRATCH_ZPB1
-		rol  a
		cmp  SCRATCH_ZPREG
		bcc  +
		sbc  SCRATCH_ZPREG
+		rol  SCRATCH_ZPB1
		dex
		bne  -

		ldx  SCRATCH_ZPB1
		rts
	}}
}

asmsub  divmod_words  (uword divisor @ AY) -> clobbers(X) -> (uword @ AY)  {
	; ---- divide two words (16 bit each) into 16 bit results
	;      input:  SCRATCH_ZPWORD1 in ZP: 16 bit number, A/Y: 16 bit divisor
	;      output: SCRATCH_ZPWORD1 in ZP: 16 bit result, A/Y: 16 bit remainder
	;      division by zero will result in quotient = 65535 and remainder = divident

	%asm {{
remainder = SCRATCH_ZPB1

		sta  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		lda  #0	        		;preset remainder to 0
		sta  remainder
		sta  remainder+1
		ldx  #16	        	;repeat for each bit: ...

-		asl  SCRATCH_ZPWORD1		;number lb & hb*2, msb -> Carry
		rol  SCRATCH_ZPWORD1+1
		rol  remainder			;remainder lb & hb * 2 + msb from carry
		rol  remainder+1
		lda  remainder
		sec
		sbc  SCRATCH_ZPWORD2		;substract divisor to see if it fits in
		tay	        		;lb result -> Y, for we may need it later
		lda  remainder+1
		sbc  SCRATCH_ZPWORD2+1
		bcc  +				;if carry=0 then divisor didn't fit in yet

		sta  remainder+1		;else save substraction result as new remainder,
		sty  remainder
		inc  SCRATCH_ZPWORD1		;and INCrement result cause divisor fit in 1 times

+		dex
		bne  -

		lda  remainder			; copy remainder to ZPWORD2 result register
		sta  SCRATCH_ZPWORD2
		lda  remainder+1
		sta  SCRATCH_ZPWORD2+1

		lda  SCRATCH_ZPWORD1		; load division result in A/Y
		ldy  SCRATCH_ZPWORD1+1

		rts

	}}
}

asmsub  randseed  (uword seed @ AY) -> clobbers(A, Y) -> ()  {
	; ---- reset the random seeds for the byte and word random generators
	;      default starting values are:  A=$2c Y=$9e  
	%asm {{
		sta  randword._seed
		sty  randword._seed+1
		stx  SCRATCH_ZPREG
		clc
		adc  #14
		sta  randbyte._seed
		ora  #$80		; make negative
		jsr  c64.FREADSA
		jsr  c64.RND		; reseed the float rng using the (negative) number in A
		ldx  SCRATCH_ZPREG
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


}
