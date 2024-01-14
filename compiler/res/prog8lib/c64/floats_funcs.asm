; --- floating point builtin functions


func_sign_f_into_A	.proc
		jsr  MOVFM
		jmp  SIGN
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

func_abs_f_into_FAC1    .proc
        jsr  MOVFM
        jmp  ABS
	.pend

func_sqrt_into_FAC1     .proc
        jsr  MOVFM
        jmp  SQR
	.pend



containment_floatarray    .proc
	; -- check if a value exists in a float array.
	;    parameters: FAC1: value to check, P8ZP_SCRATCH_W1: address of the word array, Y = length of array (>=1).
	;    returns boolean 0/1 in A.
	sty  P8ZP_SCRATCH_REG
	ldx  #<floats.floats_temp_var
	ldy  #>floats.floats_temp_var
	jsr  floats.MOVMF
	ldx  P8ZP_SCRATCH_REG
	ldy  #0
-       lda  floats.floats_temp_var
	cmp  (P8ZP_SCRATCH_W1),y
	bne  _firstmiss
	iny
	lda  floats.floats_temp_var+1
	cmp  (P8ZP_SCRATCH_W1),y
	bne  _secondmiss
	iny
	lda  floats.floats_temp_var+2
	cmp  (P8ZP_SCRATCH_W1),y
	bne  _thirdmiss
	iny
	lda  floats.floats_temp_var+3
	cmp  (P8ZP_SCRATCH_W1),y
	bne  _fourthmiss
	iny
	lda  floats.floats_temp_var+4
	cmp  (P8ZP_SCRATCH_W1),y
	bne  _fifthmiss
	lda  #1
	rts

_firstmiss
	iny
_secondmiss
	iny
_thirdmiss
	iny
_fourthmiss
	iny
_fifthmiss
        iny
	dex
	bne  -
        lda  #0
        rts

	.pend
