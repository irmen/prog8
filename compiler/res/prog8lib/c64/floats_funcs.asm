; --- floating point builtin functions


func_atan_stack	.proc
		jsr  func_atan_fac1
		jmp  push_fac1
		.pend

func_atan_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  ATN
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_ceil_stack	.proc
		jsr  func_ceil_fac1
		jmp  push_fac1
		.pend

func_ceil_fac1	.proc
		; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		ldx  #<fmath_float1
		ldy  #>fmath_float1
		jsr  MOVMF
		jsr  INT
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  FCOMP
		cmp  #0
		beq  +
		lda  #<FL_ONE_const
		ldy  #>FL_ONE_const
		jsr  FADD
+		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_floor_stack	.proc
		jsr  func_floor_fac1
		jmp  push_fac1
		.pend

func_floor_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  INT
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_round_stack	.proc
		jsr  func_round_fac1
		jmp  push_fac1
		.pend

func_round_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  FADDH
		jsr  INT
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_sin_stack	.proc
		jsr  func_sin_fac1
		jmp  push_fac1
		.pend

func_sin_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  SIN
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_cos_stack	.proc
		jsr  func_cos_fac1
		jmp  push_fac1
		.pend

func_cos_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  COS
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_tan_stack	.proc
		jsr  func_tan_fac1
		jmp  push_fac1
		.pend

func_tan_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  TAN
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_rad_stack	.proc
		jsr  func_rad_fac1
		jmp  push_fac1
		.pend

func_rad_fac1	.proc
		; -- convert degrees to radians (d * pi / 180)
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		lda  #<_pi_div_180
		ldy  #>_pi_div_180
		jsr  FMULT
		ldx  P8ZP_SCRATCH_REG
		rts
_pi_div_180	.byte 123, 14, 250, 53, 18		; pi / 180
		.pend

func_deg_stack	.proc
		jsr  func_deg_fac1
		jmp  push_fac1
		.pend

func_deg_fac1	.proc
		; -- convert radians to degrees (d * (1/ pi * 180))
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		lda  #<_one_over_pi_div_180
		ldy  #>_one_over_pi_div_180
		jsr  FMULT
		ldx  P8ZP_SCRATCH_REG
		rts
_one_over_pi_div_180	.byte 134, 101, 46, 224, 211		; 1 / (pi * 180)
		.pend

func_ln_stack	.proc
		jsr  func_ln_fac1
		jmp  push_fac1
		.pend

func_ln_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  LOG
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_log2_stack	.proc
		jsr  func_log2_fac1
		jmp  push_fac1
		.pend

func_log2_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  LOG
		jsr  MOVEF
		lda  #<FL_LOG2_const
		ldy  #>FL_LOG2_const
		jsr  MOVFM
		jsr  FDIVT
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_sign_f_stack	.proc
		jsr  func_sign_f_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sign_f_into_A	.proc
		jsr  MOVFM
		jmp  SIGN
		.pend

func_sqrt_stack	.proc
		jsr  func_sqrt_fac1
		jmp  push_fac1
		.pend

func_sqrt_fac1	.proc
		jsr  MOVFM
		stx  P8ZP_SCRATCH_REG
		jsr  SQR
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_rndf_stack	.proc
		jsr  func_rndf_fac1
		jmp  push_fac1
		.pend

func_rndf_fac1	.proc
		stx  P8ZP_SCRATCH_REG
		lda  #1
		jsr  FREADSA
		jsr  RND		; rng into fac1
		ldx  P8ZP_SCRATCH_REG
		rts
		.pend

func_swap_f	.proc
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

func_reverse_f	.proc
		; --- reverse an array of floats (array in P8ZP_SCRATCH_W1, num elements in A)
_left_index = P8ZP_SCRATCH_W2
_right_index = P8ZP_SCRATCH_W2+1
_loop_count = P8ZP_SCRATCH_REG
		pha
		jsr  a_times_5
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



a_times_5	.proc
		sta  P8ZP_SCRATCH_B1
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_B1
		rts
		.pend

func_any_f_into_A	.proc
		jsr  a_times_5
		jmp  prog8_lib.func_any_b_into_A
		.pend

func_all_f_into_A	.proc
		jsr  a_times_5
		jmp  prog8_lib.func_all_b_into_A
		.pend

func_any_f_stack	.proc
		jsr  a_times_5
		jmp  prog8_lib.func_any_b_stack
		.pend

func_all_f_stack	.proc
		jsr  a_times_5
		jmp  prog8_lib.func_all_b_stack
		.pend
