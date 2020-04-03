; --- low level floating point assembly routines for the C64

ub2float	.proc
		; -- convert ubyte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		ldy  c64.SCRATCH_ZPB1
		jsr  FREADUY
_fac_to_mem	ldx  c64.SCRATCH_ZPWORD2
		ldy  c64.SCRATCH_ZPWORD2+1
		jsr  MOVMF
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend

b2float		.proc
		; -- convert byte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		lda  c64.SCRATCH_ZPB1
		jsr  FREADSA
		jmp  ub2float._fac_to_mem
		.pend

uw2float	.proc
		; -- convert uword in SCRATCH_ZPWORD1 to float at address A/Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  GIVUAYFAY
		jmp  ub2float._fac_to_mem
		.pend

w2float		.proc
		; -- convert word in SCRATCH_ZPWORD1 to float at address A/Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		ldy  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
		jsr  GIVAYF
		jmp  ub2float._fac_to_mem
		.pend

stack_b2float	.proc
		; -- b2float operating on the stack
		inx
		lda  c64.ESTACK_LO,x
		stx  c64.SCRATCH_ZPREGX
		jsr  FREADSA
		jmp  push_fac1_as_result
		.pend

stack_w2float	.proc
		; -- w2float operating on the stack
		inx
		ldy  c64.ESTACK_LO,x
		lda  c64.ESTACK_HI,x
		stx  c64.SCRATCH_ZPREGX
		jsr  GIVAYF
		jmp  push_fac1_as_result
		.pend

stack_ub2float	.proc
		; -- ub2float operating on the stack
		inx
		lda  c64.ESTACK_LO,x
		stx  c64.SCRATCH_ZPREGX
		tay
		jsr  FREADUY
		jmp  push_fac1_as_result
		.pend

stack_uw2float	.proc
		; -- uw2float operating on the stack
		inx
		lda  c64.ESTACK_LO,x
		ldy  c64.ESTACK_HI,x
		stx  c64.SCRATCH_ZPREGX
		jsr  GIVUAYFAY
		jmp  push_fac1_as_result
		.pend

stack_float2w	.proc               ; also used for float2b
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  AYINT
		ldx  c64.SCRATCH_ZPREGX
		lda  $64
		sta  c64.ESTACK_HI,x
		lda  $65
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend

stack_float2uw	.proc               ; also used for float2ub
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  GETADR
		ldx  c64.SCRATCH_ZPREGX
		sta  c64.ESTACK_HI,x
		tya
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend

push_float	.proc
		; ---- push mflpt5 in A/Y onto stack
		; (taking 3 stack positions = 6 bytes of which 1 is padding)
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #0
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_LO,x
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_HI,x
		dex
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_LO,x
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_HI,x
		dex
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend

func_rndf	.proc
		; -- put a random floating point value on the stack
		stx  c64.SCRATCH_ZPREG
		lda  #1
		jsr  FREADSA
		jsr  RND		; rng into fac1
		ldx  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jsr  MOVMF	; fac1 to mem X/Y
		ldx  c64.SCRATCH_ZPREG
		lda  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jmp  push_float
_rndf_rnum5	.byte  0,0,0,0,0
		.pend

push_float_from_indexed_var	.proc
		; -- push the float from the array at A/Y with index on stack, onto the stack.
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		jsr  prog8_lib.pop_index_times_5
		jsr  prog8_lib.add_a_to_zpword
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jmp  push_float
		.pend

pop_float	.proc
		; ---- pops mflpt5 from stack to memory A/Y
		; (frees 3 stack positions = 6 bytes of which 1 is padding)
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #4
		inx
		lda  c64.ESTACK_LO,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		inx
		lda  c64.ESTACK_HI,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		lda  c64.ESTACK_LO,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		inx
		lda  c64.ESTACK_HI,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		lda  c64.ESTACK_LO,x
		sta  (c64.SCRATCH_ZPWORD1),y
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

pop_float_to_indexed_var	.proc
		; -- pop the float on the stack, to the memory in the array at A/Y indexed by the byte on stack
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		jsr  prog8_lib.pop_index_times_5
		jsr  prog8_lib.add_a_to_zpword
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jmp  pop_float
		.pend

copy_float	.proc
		; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1,
		;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		ldy  #0
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		rts
		.pend

inc_var_f	.proc
		; -- add 1 to float pointed to by A/Y
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		stx  c64.SCRATCH_ZPREGX
		jsr  MOVFM
		lda  #<FL_FONE
		ldy  #>FL_FONE
		jsr  FADD
		ldx  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  MOVMF
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend

dec_var_f	.proc
		; -- subtract 1 from float pointed to by A/Y
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		stx  c64.SCRATCH_ZPREGX
		lda  #<FL_FONE
		ldy  #>FL_FONE
		jsr  MOVFM
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  FSUB
		ldx  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  MOVMF
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend

inc_indexed_var_f	.proc
		; -- add 1 to float in array pointed to by A/Y, at index X
		pha
		txa
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1
		sta  c64.SCRATCH_ZPB1
		pla
		clc
		adc  c64.SCRATCH_ZPB1
		bcc  +
		iny
+		jmp  inc_var_f
		.pend

dec_indexed_var_f	.proc
		; -- subtract 1 to float in array pointed to by A/Y, at index X
		pha
		txa
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1
		sta  c64.SCRATCH_ZPB1
		pla
		clc
		adc  c64.SCRATCH_ZPB1
		bcc  +
		iny
+		jmp  dec_var_f
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
		ldx  c64.SCRATCH_ZPREGX
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
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  CONUPK		; fac2 = float1
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  FPWR
		ldx  c64.SCRATCH_ZPREGX
		jmp  push_fac1_as_result
		.pend

div_f		.proc
		; -- push f1/f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FDIV
		jmp  push_fac1_as_result
		.pend

add_f		.proc
		; -- push f1+f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FADD
		jmp  push_fac1_as_result
		.pend

sub_f		.proc
		; -- push f1-f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FSUB
		jmp  push_fac1_as_result
		.pend

mul_f		.proc
		; -- push f1*f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FMULT
		jmp  push_fac1_as_result
		.pend

neg_f		.proc
		; -- push -flt back on stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  NEGOP
		jmp  push_fac1_as_result
		.pend

abs_f		.proc
		; -- push abs(float) on stack (as float)
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  ABS
		jmp  push_fac1_as_result
		.pend

equal_f		.proc
		; -- are the two mflpt5 numbers on the stack identical?
		inx
		inx
		inx
		inx
		lda  c64.ESTACK_LO-3,x
		cmp  c64.ESTACK_LO,x
		bne  _equals_false
		lda  c64.ESTACK_LO-2,x
		cmp  c64.ESTACK_LO+1,x
		bne  _equals_false
		lda  c64.ESTACK_LO-1,x
		cmp  c64.ESTACK_LO+2,x
		bne  _equals_false
		lda  c64.ESTACK_HI-2,x
		cmp  c64.ESTACK_HI+1,x
		bne  _equals_false
		lda  c64.ESTACK_HI-1,x
		cmp  c64.ESTACK_HI+2,x
		bne  _equals_false
_equals_true	lda  #1
_equals_store	inx
		sta  c64.ESTACK_LO+1,x
		rts
_equals_false	lda  #0
		beq  _equals_store
		.pend

notequal_f	.proc
		; -- are the two mflpt5 numbers on the stack different?
		jsr  equal_f
		eor  #1		; invert the result
		sta  c64.ESTACK_LO+1,x
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
		stx  c64.SCRATCH_ZPREG
		jsr  FCOMP		; A = flt1 compared with flt2 (0=equal, 1=flt1>flt2, 255=flt1<flt2)
		ldx  c64.SCRATCH_ZPREG
		rts
_return_false	lda  #0
_return_result  sta  c64.ESTACK_LO,x
		dex
		rts
_return_true	lda  #1
		bne  _return_result
		.pend

func_sin	.proc
		; -- push sin(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  SIN
		jmp  push_fac1_as_result
		.pend

func_cos	.proc
		; -- push cos(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  COS
		jmp  push_fac1_as_result
		.pend

func_tan	.proc
		; -- push tan(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  TAN
		jmp  push_fac1_as_result
		.pend

func_atan	.proc
		; -- push atan(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  ATN
		jmp  push_fac1_as_result
		.pend

func_ln		.proc
		; -- push ln(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  LOG
		jmp  push_fac1_as_result
		.pend

func_log2	.proc
		; -- push log base 2, ln(f)/ln(2), back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
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
		stx  c64.SCRATCH_ZPREGX
		jsr  SQR
		jmp  push_fac1_as_result
		.pend

func_rad	.proc
		; -- convert degrees to radians (d * pi / 180)
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<_pi_div_180
		ldy  #>_pi_div_180
		jsr  FMULT
		jmp  push_fac1_as_result
_pi_div_180	.byte 123, 14, 250, 53, 18		; pi / 180
		.pend

func_deg	.proc
		; -- convert radians to degrees (d * (1/ pi * 180))
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<_one_over_pi_div_180
		ldy  #>_one_over_pi_div_180
		jsr  FMULT
		jmp  push_fac1_as_result
_one_over_pi_div_180	.byte 134, 101, 46, 224, 211		; 1 / (pi * 180)
		.pend

func_round	.proc
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  FADDH
		jsr  INT
		jmp  push_fac1_as_result
		.pend

func_floor	.proc
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  INT
		jmp  push_fac1_as_result
		.pend

func_ceil	.proc
		; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		ldx  #<fmath_float1
		ldy  #>fmath_float1
		jsr  MOVMF
		jsr  INT
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FCOMP
		cmp  #0
		beq  +
		lda  #<FL_FONE
		ldy  #>FL_FONE
		jsr  FADD
+		jmp  push_fac1_as_result
		.pend

func_any_f	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1	; times 5 because of float
		jmp  prog8_lib.func_any_b._entry
		.pend

func_all_f	.proc
		inx
		jsr  prog8_lib.peek_address
		lda  c64.ESTACK_LO,x	; array size
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1	; times 5 because of float
		tay
		dey
-		lda  (c64.SCRATCH_ZPWORD1),y
		clc
		dey
		adc  (c64.SCRATCH_ZPWORD1),y
		dey
		adc  (c64.SCRATCH_ZPWORD1),y
		dey
		adc  (c64.SCRATCH_ZPWORD1),y
		dey
		adc  (c64.SCRATCH_ZPWORD1),y
		dey
		cmp  #0
		beq  +
		cpy  #255
        	bne  -
		lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
+		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_max_f	.proc
		lda  #255
		sta  _minmax_cmp+1
		lda  #<_largest_neg_float
		ldy  #>_largest_neg_float
_minmax_entry	jsr  MOVFM
		jsr  prog8_lib.pop_array_and_lengthmin1Y
		stx  c64.SCRATCH_ZPREGX
-		sty  c64.SCRATCH_ZPREG
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  FCOMP
_minmax_cmp	cmp  #255			; modified
		bne  +
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  MOVFM
+		lda  c64.SCRATCH_ZPWORD1
		clc
		adc  #5
		sta  c64.SCRATCH_ZPWORD1
		bcc  +
		inc  c64.SCRATCH_ZPWORD1+1
+		ldy  c64.SCRATCH_ZPREG
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
		lda  #<FL_ZERO
		ldy  #>FL_ZERO
		jsr  MOVFM
		jsr  prog8_lib.pop_array_and_lengthmin1Y
		stx  c64.SCRATCH_ZPREGX
-		sty  c64.SCRATCH_ZPREG
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  FADD
		ldy  c64.SCRATCH_ZPREG
		dey
		cpy  #255
		beq  +
		lda  c64.SCRATCH_ZPWORD1
		clc
		adc  #5
		sta  c64.SCRATCH_ZPWORD1
		bcc  -
		inc  c64.SCRATCH_ZPWORD1+1
		bne  -
+		jmp  push_fac1_as_result
		.pend

sign_f		.proc
		jsr  pop_float_fac1
		jsr  SIGN
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend


set_0_array_float	.proc
		; -- set a float in an array to zero (index on stack, array in SCRATCH_ZPWORD1)
		inx
		lda  c64.ESTACK_LO,x
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO,x
		tay
		lda  #0
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		sta  (c64.SCRATCH_ZPWORD1),y
		rts
		.pend


set_array_float		.proc
		; -- set a float in an array to a value (index on stack, float in SCRATCH_ZPWORD1, array in SCRATCH_ZPWORD2)
		inx
		lda  c64.ESTACK_LO,x
		asl  a
		asl  a
		clc
		adc  c64.ESTACK_LO,x
		clc
		adc  c64.SCRATCH_ZPWORD2
		ldy  c64.SCRATCH_ZPWORD2+1
		bcc  +
		iny
+		jmp  copy_float
			; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1,
			;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		.pend
