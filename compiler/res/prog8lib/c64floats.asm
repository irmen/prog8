; --- low level floating point assembly routines for the C64


ub2float	.proc
		; -- convert ubyte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  P8ZP_SCRATCH_REG_X
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  P8ZP_SCRATCH_B1
		lda  #0
		jsr  GIVAYF
_fac_to_mem	ldx  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		jsr  MOVMF
		ldx  P8ZP_SCRATCH_REG_X
		rts
		.pend

b2float		.proc
		; -- convert byte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  P8ZP_SCRATCH_REG_X
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  P8ZP_SCRATCH_B1
		jsr  FREADSA
		jmp  ub2float._fac_to_mem
		.pend

uw2float	.proc
		; -- convert uword in SCRATCH_ZPWORD1 to float at address A/Y
		stx  P8ZP_SCRATCH_REG_X
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  GIVUAYFAY
		jmp  ub2float._fac_to_mem
		.pend

w2float		.proc
		; -- convert word in SCRATCH_ZPWORD1 to float at address A/Y
		stx  P8ZP_SCRATCH_REG_X
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		jsr  GIVAYF
		jmp  ub2float._fac_to_mem
		.pend

stack_b2float	.proc
		; -- b2float operating on the stack
		inx
		lda  P8ESTACK_LO,x
		stx  P8ZP_SCRATCH_REG_X
		jsr  FREADSA
		jmp  push_fac1_as_result
		.pend

stack_w2float	.proc
		; -- w2float operating on the stack
		inx
		ldy  P8ESTACK_LO,x
		lda  P8ESTACK_HI,x
		stx  P8ZP_SCRATCH_REG_X
		jsr  GIVAYF
		jmp  push_fac1_as_result
		.pend

stack_ub2float	.proc
		; -- ub2float operating on the stack
		inx
		lda  P8ESTACK_LO,x
		stx  P8ZP_SCRATCH_REG_X
		tay
		lda  #0
		jsr  GIVAYF
		jmp  push_fac1_as_result
		.pend

stack_uw2float	.proc
		; -- uw2float operating on the stack
		inx
		lda  P8ESTACK_LO,x
		ldy  P8ESTACK_HI,x
		stx  P8ZP_SCRATCH_REG_X
		jsr  GIVUAYFAY
		jmp  push_fac1_as_result
		.pend

stack_float2w	.proc               ; also used for float2b
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  AYINT
		ldx  P8ZP_SCRATCH_REG_X
		lda  $64
		sta  P8ESTACK_HI,x
		lda  $65
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

stack_float2uw	.proc               ; also used for float2ub
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  GETADR
		ldx  P8ZP_SCRATCH_REG_X
		sta  P8ESTACK_HI,x
		tya
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

push_float	.proc
		; ---- push mflpt5 in A/Y onto stack
		; (taking 3 stack positions = 6 bytes of which 1 is padding)
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ESTACK_LO,x
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ESTACK_HI,x
		dex
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ESTACK_LO,x
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ESTACK_HI,x
		dex
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_rndf	.proc
		; -- put a random floating point value on the stack
		stx  P8ZP_SCRATCH_REG
		lda  #1
		jsr  FREADSA
		jsr  RND		; rng into fac1
		ldx  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jsr  MOVMF	; fac1 to mem X/Y
		ldx  P8ZP_SCRATCH_REG
		lda  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jmp  push_float
_rndf_rnum5	.byte  0,0,0,0,0
		.pend

pop_float	.proc
		; ---- pops mflpt5 from stack to memory A/Y
		; (frees 3 stack positions = 6 bytes of which 1 is padding)
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #4
		inx
		lda  P8ESTACK_LO,x
		sta  (P8ZP_SCRATCH_W1),y
		dey
		inx
		lda  P8ESTACK_HI,x
		sta  (P8ZP_SCRATCH_W1),y
		dey
		lda  P8ESTACK_LO,x
		sta  (P8ZP_SCRATCH_W1),y
		dey
		inx
		lda  P8ESTACK_HI,x
		sta  (P8ZP_SCRATCH_W1),y
		dey
		lda  P8ESTACK_LO,x
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

pop_float_fac1	.proc
		; -- pops float from stack into FAC1
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jmp  MOVFM
		.pend

pop_float_fac2	.proc
		; -- pops float from stack into FAC2
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jmp  CONUPK
		.pend

pop_float_to_indexed_var	.proc
		; -- pop the float on the stack, to the memory in the array at A/Y indexed by the byte on stack
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		jsr  prog8_lib.pop_index_times_5
		jsr  prog8_lib.add_a_to_zpword
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jmp  pop_float
		.pend

copy_float	.proc
		; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1,
		;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		sta  _target+1
		sty  _target+2
		ldy  #4
_loop		lda  (P8ZP_SCRATCH_W1),y
_target		sta  $ffff,y			; modified
		dey
		bpl  _loop
		rts
		.pend

inc_var_f	.proc
		; -- add 1 to float pointed to by A/Y
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		stx  P8ZP_SCRATCH_REG_X
		jsr  MOVFM
		lda  #<ONE
		ldy  #>ONE
		jsr  FADD
		ldx  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  MOVMF
		ldx  P8ZP_SCRATCH_REG_X
		rts
		.pend

dec_var_f	.proc
		; -- subtract 1 from float pointed to by A/Y
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<ONE
		ldy  #>ONE
		jsr  MOVFM
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  FSUB
		ldx  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  MOVMF
		ldx  P8ZP_SCRATCH_REG_X
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
		jmp  MOVFM
		.pend


fmath_float1	.byte 0,0,0,0,0	; storage for a mflpt5 value
fmath_float2	.byte 0,0,0,0,0	; storage for a mflpt5 value

push_fac1_as_result	.proc
		; -- push the float in FAC1 onto the stack, and return from calculation
		ldx  #<fmath_float1
		ldy  #>fmath_float1
		jsr  MOVMF
		lda  #<fmath_float1
		ldy  #>fmath_float1
		ldx  P8ZP_SCRATCH_REG_X
		jmp  push_float
		.pend

pow_f		.proc
		; -- push f1 ** f2 on stack
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		stx  P8ZP_SCRATCH_REG_X
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  CONUPK		; fac2 = float1
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  FPWR
		ldx  P8ZP_SCRATCH_REG_X
		jmp  push_fac1_as_result
		.pend

div_f		.proc
		; -- push f1/f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FDIV
		jmp  push_fac1_as_result
		.pend

add_f		.proc
		; -- push f1+f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FADD
		jmp  push_fac1_as_result
		.pend

sub_f		.proc
		; -- push f1-f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FSUB
		jmp  push_fac1_as_result
		.pend

mul_f		.proc
		; -- push f1*f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FMULT
		jmp  push_fac1_as_result
		.pend

neg_f		.proc
		; -- push -flt back on stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  NEGOP
		jmp  push_fac1_as_result
		.pend

abs_f		.proc
		; -- push abs(float) on stack (as float)
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  ABS
		jmp  push_fac1_as_result
		.pend

equal_f		.proc
		; -- are the two mflpt5 numbers on the stack identical?
		inx
		inx
		inx
		inx
		lda  P8ESTACK_LO-3,x
		cmp  P8ESTACK_LO,x
		bne  _equals_false
		lda  P8ESTACK_LO-2,x
		cmp  P8ESTACK_LO+1,x
		bne  _equals_false
		lda  P8ESTACK_LO-1,x
		cmp  P8ESTACK_LO+2,x
		bne  _equals_false
		lda  P8ESTACK_HI-2,x
		cmp  P8ESTACK_HI+1,x
		bne  _equals_false
		lda  P8ESTACK_HI-1,x
		cmp  P8ESTACK_HI+2,x
		bne  _equals_false
_equals_true	lda  #1
_equals_store	inx
		sta  P8ESTACK_LO+1,x
		rts
_equals_false	lda  #0
		beq  _equals_store
		.pend

notequal_f	.proc
		; -- are the two mflpt5 numbers on the stack different?
		jsr  equal_f
		eor  #1		; invert the result
		sta  P8ESTACK_LO+1,x
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
		jsr  MOVFM		; fac1 = flt1
		lda  #<fmath_float2
		ldy  #>fmath_float2
		stx  P8ZP_SCRATCH_REG
		jsr  FCOMP		; A = flt1 compared with flt2 (0=equal, 1=flt1>flt2, 255=flt1<flt2)
		ldx  P8ZP_SCRATCH_REG
		rts
_return_false	lda  #0
_return_result  sta  P8ESTACK_LO,x
		dex
		rts
_return_true	lda  #1
		bne  _return_result
		.pend

func_sin	.proc
		; -- push sin(f) back onto stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  SIN
		jmp  push_fac1_as_result
		.pend

func_cos	.proc
		; -- push cos(f) back onto stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  COS
		jmp  push_fac1_as_result
		.pend

func_tan	.proc
		; -- push tan(f) back onto stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  TAN
		jmp  push_fac1_as_result
		.pend

func_atan	.proc
		; -- push atan(f) back onto stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  ATN
		jmp  push_fac1_as_result
		.pend

func_ln		.proc
		; -- push ln(f) back onto stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  LOG
		jmp  push_fac1_as_result
		.pend

func_log2	.proc
		; -- push log base 2, ln(f)/ln(2), back onto stack
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  LOG
		jsr  MOVEF
		lda  #<c64.FL_LOG2
		ldy  #>c64.FL_LOG2
		jsr  MOVFM
		jsr  FDIVT
		jmp  push_fac1_as_result
		.pend

func_sqrt	.proc
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  SQR
		jmp  push_fac1_as_result
		.pend

func_rad	.proc
		; -- convert degrees to radians (d * pi / 180)
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<_pi_div_180
		ldy  #>_pi_div_180
		jsr  FMULT
		jmp  push_fac1_as_result
_pi_div_180	.byte 123, 14, 250, 53, 18		; pi / 180
		.pend

func_deg	.proc
		; -- convert radians to degrees (d * (1/ pi * 180))
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		lda  #<_one_over_pi_div_180
		ldy  #>_one_over_pi_div_180
		jsr  FMULT
		jmp  push_fac1_as_result
_one_over_pi_div_180	.byte 134, 101, 46, 224, 211		; 1 / (pi * 180)
		.pend

func_round	.proc
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  FADDH
		jsr  INT
		jmp  push_fac1_as_result
		.pend

func_floor	.proc
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		jsr  INT
		jmp  push_fac1_as_result
		.pend

func_ceil	.proc
		; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
		jsr  pop_float_fac1
		stx  P8ZP_SCRATCH_REG_X
		ldx  #<fmath_float1
		ldy  #>fmath_float1
		jsr  MOVMF
		jsr  INT
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FCOMP
		cmp  #0
		beq  +
		lda  #<ONE
		ldy  #>ONE
		jsr  FADD
+		jmp  push_fac1_as_result
		.pend

func_any_f	.proc
		inx
		lda  P8ESTACK_LO,x	; array size
		sta  P8ZP_SCRATCH_B1
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_B1	; times 5 because of float
		jmp  prog8_lib.func_any_b._entry
		.pend

func_all_f	.proc
		inx
		jsr  prog8_lib.peek_address
		lda  P8ESTACK_LO,x	; array size
		sta  P8ZP_SCRATCH_B1
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_B1	; times 5 because of float
		tay
		dey
-		lda  (P8ZP_SCRATCH_W1),y
		clc
		dey
		adc  (P8ZP_SCRATCH_W1),y
		dey
		adc  (P8ZP_SCRATCH_W1),y
		dey
		adc  (P8ZP_SCRATCH_W1),y
		dey
		adc  (P8ZP_SCRATCH_W1),y
		dey
		cmp  #0
		beq  +
		cpy  #255
        	bne  -
		lda  #1
		sta  P8ESTACK_LO+1,x
		rts
+		sta  P8ESTACK_LO+1,x
		rts
		.pend

func_max_f	.proc
		lda  #255
		sta  _minmax_cmp+1
		lda  #<_largest_neg_float
		ldy  #>_largest_neg_float
_minmax_entry	jsr  MOVFM
		jsr  prog8_lib.pop_array_and_lengthmin1Y
		stx  P8ZP_SCRATCH_REG_X
-		sty  P8ZP_SCRATCH_REG
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  FCOMP
_minmax_cmp	cmp  #255			; modified
		bne  +
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  MOVFM
+		lda  P8ZP_SCRATCH_W1
		clc
		adc  #5
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		ldy  P8ZP_SCRATCH_REG
		dey
		cpy  #255
		bne  -
		jmp  push_fac1_as_result
_largest_neg_float	.byte 255,255,255,255,255		; largest negative float -1.7014118345e+38
		.pend

func_min_f	.proc
		lda  #1
		sta  func_max_f._minmax_cmp+1
		lda  #<_largest_pos_float
		ldy  #>_largest_pos_float
		jmp  func_max_f._minmax_entry
_largest_pos_float	.byte  255,127,255,255,255		; largest positive float
		rts
		.pend

func_sum_f	.proc
		lda  #<ZERO
		ldy  #>ZERO
		jsr  MOVFM
		jsr  prog8_lib.pop_array_and_lengthmin1Y
		stx  P8ZP_SCRATCH_REG_X
-		sty  P8ZP_SCRATCH_REG
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  FADD
		ldy  P8ZP_SCRATCH_REG
		dey
		cpy  #255
		beq  +
		lda  P8ZP_SCRATCH_W1
		clc
		adc  #5
		sta  P8ZP_SCRATCH_W1
		bcc  -
		inc  P8ZP_SCRATCH_W1+1
		bne  -
+		jmp  push_fac1_as_result
		.pend

sign_f		.proc
		jsr  pop_float_fac1
		jsr  SIGN
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend


set_0_array_float	.proc
		; -- set a float in an array to zero (index on stack, array in SCRATCH_ZPWORD1)
		inx
		lda  P8ESTACK_LO,x
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO,x
		tay
		lda  #0
		sta  (P8ZP_SCRATCH_W1),y
		iny
		sta  (P8ZP_SCRATCH_W1),y
		iny
		sta  (P8ZP_SCRATCH_W1),y
		iny
		sta  (P8ZP_SCRATCH_W1),y
		iny
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend


set_array_float		.proc
		; -- set a float in an array to a value (index on stack, float in SCRATCH_ZPWORD1, array in SCRATCH_ZPWORD2)
		inx
		lda  P8ESTACK_LO,x
		asl  a
		asl  a
		clc
		adc  P8ESTACK_LO,x
		adc  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		bcc  +
		iny
+		jmp  copy_float
			; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1,
			;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		.pend


swap_floats	.proc
		; -- swap floats pointed to by SCRATCH_ZPWORD1, SCRATCH_ZPWORD2
		ldy  #4
-               lda  (P8ZP_SCRATCH_W1),y
		pha
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		pla
		sta  (P8ZP_SCRATCH_W2),y
		dey
		bpl  -
		rts
		.pend
