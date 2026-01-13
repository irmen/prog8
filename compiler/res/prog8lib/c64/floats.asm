; --- low level floating point assembly routines for the C64

FL_ONE_const	.byte  129     			; 1.0
FL_ZERO_const	.byte  0,0,0,0,0		; 0.0
; note: don't add too many constants here because they all end up in the resulting program


		.section BSS
floats_temp_var         .byte  ?,?,?,?,?        ; temporary storage for a float
		.send BSS

ub2float	.proc
		; -- convert ubyte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, X, Y
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  P8ZP_SCRATCH_B1
		lda  #0
		jsr  GIVAYF
_fac_to_mem	ldx  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		jmp  MOVMF
		.pend

b2float		.proc
		; -- convert byte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, X, Y
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  P8ZP_SCRATCH_B1
		jsr  FREADSA
		jmp  ub2float._fac_to_mem
		.pend

uw2float	.proc
		; -- convert uword in SCRATCH_ZPWORD1 to float at address A/Y
		;    clobbers X
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  GIVUAYFAY
		jmp  ub2float._fac_to_mem
		.pend

w2float		.proc
		; -- convert word in SCRATCH_ZPWORD1 to float at address A/Y
		;    clobbers X
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W1+1
		jsr  GIVAYF
		jmp  ub2float._fac_to_mem
		.pend


cast_from_uw	.proc
		; -- uword in A/Y into float var at (P8ZP_SCRATCH_W2)
		;    clobbers X
		jsr  GIVUAYFAY
		jmp  ub2float._fac_to_mem
		.pend


cast_from_w	.proc
		; -- word in A/Y into float var at (P8ZP_SCRATCH_W2)
		;    clobbers X
		jsr  GIVAYFAY
		jmp  ub2float._fac_to_mem
		.pend


cast_from_ub	.proc
		; -- ubyte in Y into float var at (P8ZP_SCRATCH_W2)
		;    clobbers X
		jsr  FREADUY
		jmp  ub2float._fac_to_mem
		.pend


cast_from_b	.proc
		; -- byte in A into float var at (P8ZP_SCRATCH_W2)
		;    clobbers X
		jsr  FREADSA
		jmp  ub2float._fac_to_mem
		.pend

cast_as_uw_into_ya	.proc               ; also used for float 2 ub
		; -- cast float at A/Y to uword into Y/A
		;    clobbers X
		jsr  MOVFM
		jmp  cast_FAC1_as_uw_into_ya
		.pend

cast_as_w_into_ay	.proc               ; also used for float 2 b
		; -- cast float at A/Y to word into A/Y
		;    clobbers X
		jsr  MOVFM
		jmp  cast_FAC1_as_w_into_ay
		.pend

cast_as_bool_into_a	.proc
		; -- cast float at A/Y to bool into A
		;    clobbers X
		jsr  MOVFM
		jmp  cast_FAC1_as_bool_into_a
		.pend

cast_FAC1_as_bool_into_a	.proc
		; -- cast fac1 to bool into A
		;    clobbers X
		jsr  SIGN
		and  #1
		rts
		.pend

cast_FAC1_as_uw_into_ya	.proc               ; also used for float 2 ub
		; -- cast fac1 to uword into Y/A
		;    clobbers X
		jmp  GETADR     ; into Y/A
		.pend

cast_FAC1_as_w_into_ay	.proc               ; also used for float 2 b
		; -- cast fac1 to word into A/Y.  clobbers X
		jmp  AYINT2
		.pend


cast_as_long            .proc
        ; convert float pointed to by R0 into a long pointed to by AY
        ; the reverse routine that converts a long to a float is elsewhere as internal_long_to_float()

        FACHO = FAC_ADDR + 1

        ; save the target variable pointer on the stack
        pha
        tya
        pha

        lda  cx16.r0L
        ldy  cx16.r0H
        jsr  MOVFM
        jsr  QINT

        ; restore the target variable pointer from the stack, and put the result in it
        pla
        sta  P8ZP_SCRATCH_PTR+1
        pla
        sta  P8ZP_SCRATCH_PTR

        ldx  #3
        ldy  #0
-       lda  FACHO,x
        sta  (P8ZP_SCRATCH_PTR),y
        iny
        dex
        bpl -
        rts
        .pend



copy_float	.proc
		; -- copies the 5 bytes of the mflt value pointed to by P8ZP_SCRATCH_W1,
		;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		rts
		.pend

copy_float2	.proc
		; -- copies the 5 bytes of the mflt value pointed to by P8ZP_SCRATCH_W2,
		;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

inc_var_f	.proc
		; -- add 1 to float pointed to by A/Y
		;    clobbers X
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		jsr  MOVFM
		lda  #<FL_ONE_const
		ldy  #>FL_ONE_const
		jsr  FADD
		ldx  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jmp  MOVMF
		.pend

dec_var_f	.proc
		; -- subtract 1 from float pointed to by A/Y
		;    clobbers X
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  #<FL_ONE_const
		ldy  #>FL_ONE_const
		jsr  MOVFM
		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jsr  FSUB
		ldx  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		jmp  MOVMF
		.pend

		.section BSS
fmath_float1	.byte ?,?,?,?,?	; storage for a mflpt5 value
fmath_float2	.byte ?,?,?,?,?	; storage for a mflpt5 value
		.send BSS

var_fac1_less_f	.proc
		; -- is the float in FAC1 < the variable AY? Result in A. Clobbers X.
		jsr  FCOMP
		cmp  #255
		beq  +
		lda  #0
		rts
+		lda  #1
		rts
		.pend

var_fac1_lesseq_f	.proc
		; -- is the float in FAC1 <= the variable AY?  Result in A. Clobbers X.
		jsr  FCOMP
		cmp  #0
		beq  +
		cmp  #255
		beq  +
		lda  #0
		rts
+		lda  #1
		rts
		.pend

var_fac1_greater_f	.proc
		; -- is the float in FAC1 > the variable AY?  Result in A. Clobbers X.
		jsr  FCOMP
		cmp  #1
		beq  +
		lda  #0
		rts
+		lda  #1
		rts
		.pend

var_fac1_greatereq_f	.proc
		; -- is the float in FAC1 >= the variable AY?  Result in A. Clobbers X.
		jsr  FCOMP
		cmp  #0
		beq  +
		cmp  #1
		beq  +
		lda  #0
		rts
+		lda  #1
		rts
		.pend

var_fac1_equal_f	.proc
		; -- are the floats numbers in FAC1 and the variable AY identical?   Result in A. Clobbers X.
		jsr  FCOMP
		and  #1
		eor  #1
		rts
		.pend

var_fac1_notequal_f	.proc
		; -- are the floats numbers in FAC1 and the variable AY *not* identical?   Result in A. Clobbers X.
		jsr  FCOMP
		and  #1
		rts
		.pend

vars_equal_f	.proc
		; -- are the mflpt5 numbers in P8ZP_SCRATCH_W1 and AY identical?  Result in A
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		cmp  (P8ZP_SCRATCH_W2),y
		bne  _false
		iny
		lda  (P8ZP_SCRATCH_W1),y
		cmp  (P8ZP_SCRATCH_W2),y
		bne  _false
		iny
		lda  (P8ZP_SCRATCH_W1),y
		cmp  (P8ZP_SCRATCH_W2),y
		bne  _false
		iny
		lda  (P8ZP_SCRATCH_W1),y
		cmp  (P8ZP_SCRATCH_W2),y
		bne  _false
		iny
		lda  (P8ZP_SCRATCH_W1),y
		cmp  (P8ZP_SCRATCH_W2),y
		bne  _false
		lda  #1
		rts
_false		lda  #0
		rts
		.pend


vars_less_f	.proc
		; -- is float in AY < float in P8ZP_SCRATCH_W2 ?   Result in A. Clobbers X.
		jsr  MOVFM
		lda  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		jsr  FCOMP
		cmp  #255
		bne  +
		lda  #1
		rts
+		lda  #0
		rts
		.pend

vars_lesseq_f	.proc
		; -- is float in AY <= float in P8ZP_SCRATCH_W2 ?  Result in A. Clobbers X.
		jsr  MOVFM
		lda  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		jsr  FCOMP
		cmp  #255
		bne  +
-		lda  #1
		rts
+		cmp  #0
		beq  -
		lda  #0
		rts
		.pend

less_f		.proc
		; -- is f1 < f2?    Result in A. Clobbers X.
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend


lesseq_f	.proc
		; -- is f1 <= f2?  Result in A. Clobbers X.
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greater_f	.proc
		; -- is f1 > f2?  Result in A. Clobbers X.
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greatereq_f	.proc
		; -- is f1 >= f2?  Result in A. Clobbers X.
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

set_array_float_from_fac1	.proc
		; -- set the float in FAC1 in the array (index in A, array in P8ZP_SCRATCH_W1)
		;    clobbers X
		sta  P8ZP_SCRATCH_B1
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_B1
		ldy  P8ZP_SCRATCH_W1+1
		clc
		adc  P8ZP_SCRATCH_W1
		bcc  +
		iny
+		tax
		jmp  MOVMF
		.pend


set_0_array_float	.proc
		; -- set a float in an array to zero (index in A, array in P8ZP_SCRATCH_W1)
		sta  P8ZP_SCRATCH_B1
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_B1
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
		; -- set a float in an array to a value (index in A, float in P8ZP_SCRATCH_W1, array in P8ZP_SCRATCH_W2)
		sta  P8ZP_SCRATCH_B1
		asl  a
		asl  a
		clc
		adc  P8ZP_SCRATCH_B1
		adc  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		bcc  +
		iny
+		jmp  copy_float
			; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1,
			;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		.pend


pushFAC1    .proc
	;-- push floating point in FAC onto the cpu stack
	; save return address
	pla
	sta  P8ZP_SCRATCH_W2
	pla
	sta  P8ZP_SCRATCH_W2+1
	ldx  #<floats.floats_temp_var
	ldy  #>floats.floats_temp_var
	jsr  floats.MOVMF
	lda  floats.floats_temp_var
	pha
	lda  floats.floats_temp_var+1
	pha
	lda  floats.floats_temp_var+2
	pha
	lda  floats.floats_temp_var+3
	pha
	lda  floats.floats_temp_var+4
	pha
	; re-push return address
	lda  P8ZP_SCRATCH_W2+1
	pha
	lda  P8ZP_SCRATCH_W2
	pha
	rts
	.pend

popFAC .proc
	; -- pop floating point value from cpu stack into FAC1 or FAC2 (
	;    carry flag clear=FAC1, carry set=FAC2
	; save return address
	pla
	sta  P8ZP_SCRATCH_W2
	pla
	sta  P8ZP_SCRATCH_W2+1
	pla
	sta  floats.floats_temp_var+4
	pla
	sta  floats.floats_temp_var+3
	pla
	sta  floats.floats_temp_var+2
	pla
	sta  floats.floats_temp_var+1
	pla
	sta  floats.floats_temp_var
	lda  #<floats.floats_temp_var
	ldy  #>floats.floats_temp_var
	bcs  +
	jsr  floats.MOVFM
	jmp  ++
+       jsr  floats.CONUPK
+	; re-push return address
	lda  P8ZP_SCRATCH_W2+1
	pha
	lda  P8ZP_SCRATCH_W2
	pha
	rts
	.pend

