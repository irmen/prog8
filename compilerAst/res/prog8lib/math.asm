; just for tests - DISFUNCTIONAL!


math_store_reg	.byte  0		; temporary storage


multiply_bytes	.proc
	; -- multiply 2 bytes A and Y, result as byte in A  (signed or unsigned)
		sta  P8ZP_SCRATCH_B1         ; num1
		sty  P8ZP_SCRATCH_REG        ; num2
		lda  #0
		beq  _enterloop
_doAdd		clc
		adc  P8ZP_SCRATCH_B1
_loop		asl  P8ZP_SCRATCH_B1
_enterloop	lsr  P8ZP_SCRATCH_REG
		bcs  _doAdd
		bne  _loop
		rts
		.pend
