; --- floating point builtin functions


func_sign_f_into_A	.proc
		; sign in A, also sets status flags
		jsr  MOVFM
		jsr  SIGN
		cmp  #0
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
