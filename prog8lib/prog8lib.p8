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
		bvc  +
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
		;    into the 5 bytes pointed to by A/Y.  Clobbers Y.
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
		; -- pop 2 floats from stack, load the second one in FAC1
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
		
fmath_float1	.fill  5	; storage for a mflpt5 value
fmath_float2	.fill  5	; storage for a mflpt5 value

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

neg_f		.proc
		; -- push -flt back on stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.NEGOP
		jmp  push_fac1_as_result
		.pend

		
add_w		.proc
		; -- push word+word
		.warn "addw check correctness"
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
		
add_uw		.proc
		.warn "add_uw check correctness"
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
		rts	; @todo inline?
		.warn "not implemented"
		.pend

sub_uw		.proc
		rts	; @todo inline?
		.warn "sub_w not implemented"
		.pend

mul_b		.proc
		rts
		.warn "mul_b not implemented"
		.pend
		
mul_ub		.proc
		rts
		.warn "mul_ub not implemented"
		.pend
		
mul_w		.proc
		rts
		.warn "mul_w not implemented"
		.pend
		
mul_uw		.proc
		rts
		.warn "mul_uw not implemented"
		.pend

div_b		.proc
		rts
		.warn "div_b not implemented"
		.pend
		
div_ub		.proc
		rts
		.warn "div_ub not implemented"
		.pend
		
div_w		.proc
		rts
		.warn "div_w not implemented"
		.pend
		
div_uw		.proc
		rts
		.warn "div_uw not implemented"
		.pend

remainder_b	.proc
		rts
		.warn "remainder_b not implemented"
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
		rts
		.warn "remainder_w not implemented"
		.pend
		
remainder_uw	.proc
		rts
		.warn "remainder_uw not implemented"
		.pend
		
equal_w		.proc
		; -- are the two words on the stack identical?
		; @todo optimize according to http://www.6502.org/tutorials/compare_beyond.html
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
		
func_abs	.proc
		rts
		.warn "abs not implemented"
		.pend
		
func_acos	.proc
		rts
		.warn "acos not implemented"
		.pend
		
func_asin	.proc
		rts
		.warn "asin not implemented"
		.pend
		
func_tan	.proc
		; -- push tan(f) back onto stack
		jsr  pop_float_fac1
		stx  SCRATCH_ZPREGX
		jsr  c64.TAN
		jmp  push_fac1_as_result
		.pend
		
func_atan	.proc
		rts
		.warn "atan not implemented"
		.pend
		
func_ln		.proc
		rts
		.warn "ln not implemented"
		.pend
		
func_log2	.proc
		rts
		.warn "log2 not implemented"
		.pend
		
func_log10	.proc
		rts
		.warn "log10 not implemented"
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
		rts
		.warn "round not implemented"
		.pend
		
func_floor	.proc
		rts
		.warn "floor not implemented"
		.pend
		
func_ceil	.proc
		rts
		.warn "ceil not implemented"
		.pend
		
func_max	.proc
		rts
		.warn "max not implemented--what does it max over???"
		.pend
		
func_min	.proc
		rts
		.warn "min not implemented--what does it min over???"
		.pend
		
func_avg	.proc
		rts
		.warn "avg not implemented--what does it avg over???"
		.pend
		
func_sum	.proc
		rts
		.warn "sum not implemented--what does it sum over???"
		.pend
		
func_len	.proc
		rts
		.warn "len not implemented--of what does it take len?"
		.pend
		
func_any	.proc
		rts
		.warn "any not implemented--of what does it do any?"
		.pend
		
func_all	.proc
		rts
		.warn "all not implemented--of what does it do all?"
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
_rndf_rnum5	.fill 5
		.pend


func_str2byte	.proc
		rts
		.warn "str2byte not implemented"
		.pend

; @todo python code for a str-to-ubyte function that doesn't use the basic rom:
;def str2ubyte(s, slen):
;    hundreds_map = {
;        0: 0,
;        1: 100,
;        2: 200
;        }
;    digitvalue = 0
;    result = 0
;    if slen==0:
;        return digitvalue
;    digitvalue = ord(s[slen-1])-48
;    slen -= 1
;    if slen==0:
;        return digitvalue
;    result = digitvalue
;    digitvalue = 10 * (ord(s[slen-1])-48)
;    result += digitvalue
;    slen -= 1
;    if slen==0:
;        return result
;    digitvalue = hundreds_map[ord(s[slen-1])-48]
;    result += digitvalue
;    return result

func_str2ubyte  .proc
		jmp  func_str2uword
		.pend

func_str2uword	.proc
		;-- convert string (address on stack) to uword number
		lda  ESTACK_LO+1,x
		sta  $22
		lda  ESTACK_HI+1,x
		sta  $23
		jsr  _strlen2233
		tya
		stx  SCRATCH_ZPREG
		jsr  c64.FREADSTR		; string to fac1
		jsr  c64.GETADR			; fac1 to unsigned word in Y/A
		ldx  SCRATCH_ZPREG
		sta  ESTACK_HI+1,x
		tya
		sta  ESTACK_LO+1,x
		rts
_strlen2233
		;-- return the length of the (zero-terminated) string at $22/$23, in Y
		ldy  #0
-		lda  ($22),y
		beq  +
		iny
		bne  -
+		rts
		.pend

func_str2word	.proc
		rts
		.warn "str2word not implemented"
		.pend

func_str2float	.proc
		rts
		.warn "str2float not implemented"
		.pend

    
	}}
}
