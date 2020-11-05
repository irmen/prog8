; ---- builtin functions


func_any_b_stack	.proc
		jsr  func_any_b_into_A
_pushA		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_all_b_stack	.proc
		jsr  func_all_b_into_A
		jmp  func_any_b_stack._pushA
		.pend

func_any_b_into_A	.proc
		; -- any(array),  array in P8ZP_SCRATCH_W1, num bytes in A
		sta  _cmp_mod+1		; self-modifying code
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		bne  _got_any
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #0
		rts
_got_any	lda  #1
		rts
		.pend


func_all_b_into_A	.proc
		; -- all(array),  array in P8ZP_SCRATCH_W1, num bytes in A
		sta  _cmp_mod+1		; self-modifying code
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  _got_not_all
		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
_got_not_all	rts
		.pend

