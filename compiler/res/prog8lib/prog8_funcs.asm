; ---- builtin functions


func_any_b_stack	.proc
		jsr  func_any_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_all_b_stack	.proc
		jsr  func_all_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
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

func_any_w_into_A	.proc
		asl  a
		jmp  func_any_b_into_A
		.pend

func_any_w_stack	.proc
		asl  a
		jsr  func_any_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_all_w_into_A	.proc
		; -- all(warray),  array in P8ZP_SCRATCH_W1, num bytes in A
		asl  a			; times 2 because of word
		sta  _cmp_mod+1		; self-modifying code
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		bne  +
		iny
		lda  (P8ZP_SCRATCH_W1),y
		bne  ++
		lda  #0
		rts
+		iny
+		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		rts
		.pend

func_all_w_stack	.proc
		jsr  func_all_w_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sin8_into_A	.proc
		tay
		lda  _sinecos8,y
		rts
_sinecos8	.char  trunc(127.0 * sin(range(256+64) * rad(360.0/256.0)))
		.pend

func_sin8u_into_A	.proc
		tay
		lda  _sinecos8u,y
		rts
_sinecos8u	.byte  trunc(128.0 + 127.5 * sin(range(256+64) * rad(360.0/256.0)))
		.pend

func_sin8_stack	.proc
		tay
		lda  func_sin8_into_A._sinecos8,y
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sin8u_stack	.proc
		tay
		lda  func_sin8u_into_A._sinecos8u,y
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_cos8_into_A	.proc
		tay
		lda  func_sin8_into_A._sinecos8+64,y
		rts
		.pend

func_cos8u_into_A	.proc
		tay
		lda  func_sin8u_into_A._sinecos8u+64,y
		rts
		.pend

func_cos8_stack	.proc
		tay
		lda  func_sin8_into_A._sinecos8+64,y
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_cos8u_stack	.proc
		tay
		lda  func_sin8u_into_A._sinecos8u+64,y
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sin16_into_AY	.proc
		tay
		lda  _sinecos8lo,y
		pha
		lda  _sinecos8hi,y
		tay
		pla
		rts
_  :=  trunc(32767.0 * sin(range(256+64) * rad(360.0/256.0)))
_sinecos8lo     .byte  <_
_sinecos8hi     .byte  >_
		.pend

func_sin16u_into_AY	.proc
		tay
		lda  _sinecos8ulo,y
		pha
		lda  _sinecos8uhi,y
		tay
		pla
		rts
_  :=  trunc(32768.0 + 32767.5 * sin(range(256+64) * rad(360.0/256.0)))
_sinecos8ulo     .byte  <_
_sinecos8uhi     .byte  >_
		.pend


func_sin16_stack	.proc
		tay
		lda  func_sin16_into_AY._sinecos8lo,y
		sta  P8ESTACK_LO,x
		lda  func_sin16_into_AY._sinecos8hi,y
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_sin16u_stack	.proc
		tay
		lda  func_sin16u_into_AY._sinecos8ulo,y
		sta  P8ESTACK_LO,x
		lda  func_sin16u_into_AY._sinecos8uhi,y
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_cos16_into_AY	.proc
		tay
		lda  func_sin16_into_AY._sinecos8lo+64,y
		pha
		lda  func_sin16_into_AY._sinecos8hi+64,y
		tay
		pla
		rts
		.pend

func_cos16u_into_AY	.proc
		tay
		lda  func_sin16u_into_AY._sinecos8ulo+64,y
		pha
		lda  func_sin16u_into_AY._sinecos8uhi+64,y
		tay
		pla
		rts
		.pend

func_cos16_stack	.proc
		tay
		lda  func_sin16_into_AY._sinecos8lo+64,y
		sta  P8ESTACK_LO,x
		lda  func_sin16_into_AY._sinecos8hi+64,y
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_cos16u_stack	.proc
		tay
		lda  func_sin16u_into_AY._sinecos8ulo+64,y
		sta  P8ESTACK_LO,x
		lda  func_sin16u_into_AY._sinecos8uhi+64,y
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

abs_b_stack	.proc
	; -- push abs(A) on stack (as byte)
		jsr  abs_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

abs_b_into_A	.proc
	; -- A = abs(A)
		cmp  #0
		bmi  +
		rts
+		eor  #$ff
		clc
		adc  #1
		rts
		.pend

abs_w_stack	.proc
	; -- push abs(AY) on stack (as word)
		jsr  abs_w_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

abs_w_into_AY	.proc
	; -- AY = abs(AY)
		cpy  #0
		bmi  +
		rts
+		eor  #$ff
		pha
		tya
		eor  #$ff
		tay
		pla
		clc
		adc  #1
		bcc  +
		iny
+		rts
		.pend

func_sign_b_into_A	.proc
		cmp  #0
		beq  _zero
		bmi  _neg
		lda  #1
_zero		rts
_neg		lda  #-1
		rts
		.pend

func_sign_b_stack	.proc
		jsr  func_sign_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sign_ub_into_A	.proc
		cmp  #0
		bne  _pos
		rts
_pos		lda  #1
		rts
		.pend

func_sign_ub_stack	.proc
		jsr  func_sign_ub_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sign_uw_into_A	.proc
		cpy  #0
		beq  _possibly_zero
_pos		lda  #1
		rts
_possibly_zero	cmp  #0
		bne  _pos
		rts
		.pend

func_sign_uw_stack	.proc
		jsr  func_sign_uw_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sign_w_into_A	.proc
		cpy  #0
		beq  _possibly_zero
		bmi  _neg
_pos		lda  #1
		rts
_neg		lda  #-1
		rts
_possibly_zero	cmp  #0
		bne  _pos
		rts
		.pend


func_sign_w_stack	.proc
		jsr  func_sign_w_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sqrt16_stack	.proc
		jsr  func_sqrt16_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_sqrt16_into_A	.proc
		; integer square root from  http://6502org.wikidot.com/software-math-sqrt
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		txa
		pha
		lda  #0
		sta  P8ZP_SCRATCH_B1
		sta  P8ZP_SCRATCH_REG
		ldx  #8
-		sec
		lda  P8ZP_SCRATCH_W1+1
		sbc  #$40
		tay
		lda  P8ZP_SCRATCH_REG
		sbc  P8ZP_SCRATCH_B1
		bcc  +
		sty  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_REG
+		rol  P8ZP_SCRATCH_B1
		asl  P8ZP_SCRATCH_W1
		rol  P8ZP_SCRATCH_W1+1
		rol  P8ZP_SCRATCH_REG
		asl  P8ZP_SCRATCH_W1
		rol  P8ZP_SCRATCH_W1+1
		rol  P8ZP_SCRATCH_REG
		dex
		bne  -
		pla
		tax
		lda  P8ZP_SCRATCH_B1
		rts
		.pend

func_fastrnd8_stack	.proc
	; -- put a random ubyte on the estack (using fast but bad RNG)
		jsr  math.fast_randbyte
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_rnd_stack	.proc
	; -- put a random ubyte on the estack
		jsr  math.randbyte
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_rndw_stack	.proc
	; -- put a random uword on the estack
		jsr  math.randword
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_min_ub_into_A	.proc
		; -- min(ubarray) -> A.  (array in P8ZP_SCRATCH_W1, num elements in A)
		tay
		dey
		lda  #255
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_B1
		bcs  +
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		rts
				.pend

func_min_ub_stack	.proc
		jsr  func_min_ub_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_min_b_into_A	.proc
		; -- min(barray) -> A.  (array in P8ZP_SCRATCH_W1, num elements in A)
		tay
		dey
		lda  #127
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		clc
		sbc  P8ZP_SCRATCH_B1
		bvc  +
		eor  #$80
+		bpl  +
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		rts
		.pend

func_min_b_stack	.proc
		jsr  func_min_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_min_uw_into_AY	.proc
		; -- min(uwarray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		asl  a
		tay
		dey
		dey
		lda  #$ff
		sta  _result_minuw
		sta  _result_minuw+1
_loop
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		cmp   _result_minuw+1
		bcc  _less
		bne  _gtequ
		lda  (P8ZP_SCRATCH_W1),y
		cmp  _result_minuw
		bcs  _gtequ
_less		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minuw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minuw+1
		dey
_gtequ		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_minuw
		ldy  _result_minuw+1
		rts
_result_minuw	.word  0
		.pend

func_min_w_into_AY	.proc
		; -- min(warray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		asl  a
		tay
		dey
		dey
		lda  #$ff
		sta  _result_minw
		lda  #$7f
		sta  _result_minw+1
_loop
		lda  (P8ZP_SCRATCH_W1),y
		cmp   _result_minw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		sbc  _result_minw+1
		bvc  +
		eor  #$80
+		bpl  _gtequ
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_minw+1
		dey
_gtequ		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_minw
		ldy  _result_minw+1
		rts
_result_minw	.word  0
		.pend

func_min_uw_stack	.proc
		jsr  func_min_uw_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_min_w_stack	.proc
		jsr  func_min_w_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_max_ub_into_A	.proc
		; -- max(ubarray) -> A  (array in P8ZP_SCRATCH_W1, num elements in A)
		tay
		dey
		lda  #0
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_B1
		bcc  +
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		rts
		.pend

func_max_ub_stack	.proc
		jsr  func_max_ub_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_max_b_into_A	.proc
		; -- max(barray) -> A  (array in P8ZP_SCRATCH_W1, num elements in A)
		tay
		lda  #-128
		sta  P8ZP_SCRATCH_B1
-		lda  (P8ZP_SCRATCH_W1),y
		sec
		sbc  P8ZP_SCRATCH_B1
		bvc  +
		eor  #$80
+		bmi  +
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_B1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_B1
		rts
		.pend

func_max_b_stack	.proc
		jsr  func_max_b_into_A
		sta  P8ESTACK_LO,x
		dex
		rts
		.pend

func_max_uw_into_AY	.proc
		; -- max(uwarray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		asl  a
		tay
		dey
		dey
		lda  #0
		sta  _result_maxuw
		sta  _result_maxuw+1
_loop
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		cmp  _result_maxuw+1
		bcc  _lesseq
		bne  _greater
		lda  (P8ZP_SCRATCH_W1),y
		cmp  _result_maxuw
		bcc  _lesseq
_greater	lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxuw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxuw+1
		dey
_lesseq		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_maxuw
		ldy  _result_maxuw+1
		rts
_result_maxuw	.word  0
		.pend

func_max_uw_stack	.proc
		jsr  func_max_uw_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_max_w_into_AY	.proc
		; -- max(uwarray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		asl  a
		tay
		dey
		dey
		lda  #0
		sta  _result_maxw
		lda  #$80
		sta  _result_maxw+1
_loop
		lda  (P8ZP_SCRATCH_W1),y
		cmp  _result_maxw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		sbc  _result_maxw+1
		bvc  +
		eor  #$80
+		bmi  _lesseq
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxw
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _result_maxw+1
		dey
_lesseq		dey
		dey
		cpy  #254
		bne  _loop
		lda  _result_maxw
		ldy  _result_maxw+1
		rts
_result_maxw	.word  0
		.pend

func_max_w_stack	.proc
		jsr  func_max_w_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_sum_ub_into_AY	.proc
		; -- sum(ubarray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		tay
		dey
		lda  #0
		sta  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2+1
-		lda  (P8ZP_SCRATCH_W1),y
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2
		bcc  +
		inc  P8ZP_SCRATCH_W2+1
+		dey
		cpy  #255
		bne  -
		lda  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		rts
		.pend

func_sum_ub_stack	.proc
		jsr  func_sum_ub_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend


func_sum_b_into_AY	.proc
		; -- sum(barray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		tay
		dey
		lda  #0
		sta  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2+1
_loop		lda  (P8ZP_SCRATCH_W1),y
		pha
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2
		; sign extend the high byte
		pla
		and  #$80
		beq  +
		lda  #$ff
+		adc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W2+1
		dey
		cpy  #255
		bne  _loop
		lda  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		rts
		.pend

func_sum_b_stack	.proc
		jsr  func_sum_b_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend

func_sum_uw_into_AY	.proc
		; -- sum(uwarray) -> AY  (array in P8ZP_SCRATCH_W1, num elements in A)
		asl  a
		tay
		dey
		dey
		lda  #0
		sta  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2+1
-		lda  (P8ZP_SCRATCH_W1),y
		iny
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W2
		lda  (P8ZP_SCRATCH_W1),y
		adc  P8ZP_SCRATCH_W2+1
		sta  P8ZP_SCRATCH_W2+1
		dey
		dey
		dey
		cpy  #254
		bne  -
		lda  P8ZP_SCRATCH_W2
		ldy  P8ZP_SCRATCH_W2+1
		rts
		.pend

func_sum_uw_stack	.proc
		jsr  func_sum_uw_into_AY
		sta  P8ESTACK_LO,x
		tya
		sta  P8ESTACK_HI,x
		dex
		rts
		.pend


func_sum_w_into_AY = func_sum_uw_into_AY
func_sum_w_stack = func_sum_uw_stack


func_sort_ub	.proc
		; 8bit unsigned sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in S
		; first, put pointer BEFORE array
		sta  P8ZP_SCRATCH_B1
		lda  P8ZP_SCRATCH_W1
		bne  +
		dec  P8ZP_SCRATCH_W1+1
+		dec  P8ZP_SCRATCH_W1
_sortloop	ldy  P8ZP_SCRATCH_B1		;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y	;last value in (what is left of) sequence to be sorted
		sta  P8ZP_SCRATCH_REG		;save value. will be over-written by largest number
		jmp  _l2
_l1		dey
		beq  _l3
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2+1
		bcc  _l1
_l2		sty  P8ZP_SCRATCH_W2	;index of potentially largest value
		sta  P8ZP_SCRATCH_W2+1	;potentially largest value
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1		;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2+1	;the largest value
		sta  (P8ZP_SCRATCH_W1),y	;put largest value in place
		ldy  P8ZP_SCRATCH_W2	;index of free space
		lda  P8ZP_SCRATCH_REG		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y	;put the over-written value in the free space
		dec  P8ZP_SCRATCH_B1		;end of the shorter sequence still left
		bne  _sortloop			;start working with the shorter sequence
		rts
		.pend


func_sort_b	.proc
		; 8bit signed sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in A
		; first, put pointer BEFORE array
		sta  P8ZP_SCRATCH_B1
		lda  P8ZP_SCRATCH_W1
		bne  +
		dec  P8ZP_SCRATCH_W1+1
+		dec  P8ZP_SCRATCH_W1
_sortloop	ldy  P8ZP_SCRATCH_B1		;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y	;last value in (what is left of) sequence to be sorted
		sta  P8ZP_SCRATCH_REG		;save value. will be over-written by largest number
		jmp  _l2
_l1		dey
		beq  _l3
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2+1
		bmi  _l1
_l2		sty  P8ZP_SCRATCH_W2	;index of potentially largest value
		sta  P8ZP_SCRATCH_W2+1	;potentially largest value
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1		;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2+1	;the largest value
		sta  (P8ZP_SCRATCH_W1),y	;put largest value in place
		ldy  P8ZP_SCRATCH_W2	;index of free space
		lda  P8ZP_SCRATCH_REG		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y	;put the over-written value in the free space
		dec  P8ZP_SCRATCH_B1		;end of the shorter sequence still left
		bne  _sortloop			;start working with the shorter sequence
		rts
		.pend


func_sort_uw	.proc
		; 16bit unsigned sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in A
		; first: subtract 2 of the pointer
		asl  a
		sta  P8ZP_SCRATCH_B1
		lda  P8ZP_SCRATCH_W1
		sec
		sbc  #2
		sta  P8ZP_SCRATCH_W1
		bcs  _sort_loop
		dec  P8ZP_SCRATCH_W1+1
_sort_loop	ldy  P8ZP_SCRATCH_B1    	;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y    ;last value in (what is left of) sequence to be sorted
		sta  _work3          		;save value. will be over-written by largest number
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _work3+1
		dey
		jmp  _l2
_l1		dey
		dey
		beq  _l3
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		cmp  P8ZP_SCRATCH_W2+1
		bne  +
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2
+		bcc  _l1
_l2		sty  _work1          		;index of potentially largest value
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2          ;potentially largest value
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2+1
		dey
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1           ;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2          ;the largest value
		sta  (P8ZP_SCRATCH_W1),y      ;put largest value in place
		iny
		lda  P8ZP_SCRATCH_W2+1
		sta  (P8ZP_SCRATCH_W1),y
		ldy  _work1         		 ;index of free space
		lda  _work3          		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y      ;put the over-written value in the free space
		iny
		lda  _work3+1
		sta  (P8ZP_SCRATCH_W1),y
		dey
		dec  P8ZP_SCRATCH_B1           ;end of the shorter sequence still left
		dec  P8ZP_SCRATCH_B1
		bne  _sort_loop           ;start working with the shorter sequence
		rts
_work1	.byte  0
_work3	.word  0
		.pend


func_sort_w	.proc
		; 16bit signed sort
		; sorting subroutine coded by mats rosengren (mats.rosengren@esa.int)
		; input:  address of array to sort in P8ZP_SCRATCH_W1, length in A
		; first: subtract 2 of the pointer
		asl  a
		sta  P8ZP_SCRATCH_B1
		lda  P8ZP_SCRATCH_W1
		sec
		sbc  #2
		sta  P8ZP_SCRATCH_W1
		bcs  _sort_loop
		dec  P8ZP_SCRATCH_W1+1
_sort_loop	ldy  P8ZP_SCRATCH_B1    	;start of subroutine sort
		lda  (P8ZP_SCRATCH_W1),y    ;last value in (what is left of) sequence to be sorted
		sta  _work3          		;save value. will be over-written by largest number
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  _work3+1
		dey
		jmp  _l2
_l1		dey
		dey
		beq  _l3
		lda  (P8ZP_SCRATCH_W1),y
		cmp  P8ZP_SCRATCH_W2
		iny
		lda  (P8ZP_SCRATCH_W1),y
		dey
		sbc  P8ZP_SCRATCH_W2+1
		bvc  +
		eor  #$80
+		bmi  _l1
_l2		sty  _work1          		;index of potentially largest value
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2          ;potentially largest value
		iny
		lda  (P8ZP_SCRATCH_W1),y
		sta  P8ZP_SCRATCH_W2+1
		dey
		jmp  _l1
_l3		ldy  P8ZP_SCRATCH_B1           ;where the largest value shall be put
		lda  P8ZP_SCRATCH_W2          ;the largest value
		sta  (P8ZP_SCRATCH_W1),y      ;put largest value in place
		iny
		lda  P8ZP_SCRATCH_W2+1
		sta  (P8ZP_SCRATCH_W1),y
		ldy  _work1         		 ;index of free space
		lda  _work3          		;the over-written value
		sta  (P8ZP_SCRATCH_W1),y      ;put the over-written value in the free space
		iny
		lda  _work3+1
		sta  (P8ZP_SCRATCH_W1),y
		dey
		dec  P8ZP_SCRATCH_B1           ;end of the shorter sequence still left
		dec  P8ZP_SCRATCH_B1
		bne  _sort_loop           ;start working with the shorter sequence
		rts
_work1	.byte  0
_work3	.word  0
		.pend


func_reverse_b	.proc
		; --- reverse an array of bytes (in-place)
		; inputs:  pointer to array in P8ZP_SCRATCH_W1, length in A
_index_right = P8ZP_SCRATCH_W2
_index_left = P8ZP_SCRATCH_W2+1
_loop_count = P8ZP_SCRATCH_REG
		sta  _loop_count
		lsr  _loop_count
		sec
		sbc  #1
		sta  _index_right
		lda  #0
		sta  _index_left
_loop		ldy  _index_right
		lda  (P8ZP_SCRATCH_W1),y
		pha
		ldy  _index_left
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _index_right
		sta  (P8ZP_SCRATCH_W1),y
		pla
		ldy  _index_left
		sta  (P8ZP_SCRATCH_W1),y
		inc  _index_left
		dec  _index_right
		dec  _loop_count
		bne  _loop
		rts
		.pend


func_reverse_w	.proc
		; --- reverse an array of words (in-place)
		; inputs:  pointer to array in P8ZP_SCRATCH_W1, length in A
_index_first = P8ZP_SCRATCH_W2
_index_second = P8ZP_SCRATCH_W2+1
_loop_count = P8ZP_SCRATCH_REG
		pha
		asl  a     ; *2 because words
		sec
		sbc  #2
		sta  _index_first
		lda  #0
		sta  _index_second
		pla
		lsr  a
		pha
		sta  _loop_count
		; first reverse the lsbs
_loop_lo	ldy  _index_first
		lda  (P8ZP_SCRATCH_W1),y
		pha
		ldy  _index_second
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _index_first
		sta  (P8ZP_SCRATCH_W1),y
		pla
		ldy  _index_second
		sta  (P8ZP_SCRATCH_W1),y
		inc  _index_second
		inc  _index_second
		dec  _index_first
		dec  _index_first
		dec  _loop_count
		bne  _loop_lo
		; now reverse the msbs
		dec  _index_second
		inc  _index_first
		inc  _index_first
		inc  _index_first
		pla
		sta  _loop_count
_loop_hi	ldy  _index_first
		lda  (P8ZP_SCRATCH_W1),y
		pha
		ldy  _index_second
		lda  (P8ZP_SCRATCH_W1),y
		ldy  _index_first
		sta  (P8ZP_SCRATCH_W1),y
		pla
		ldy  _index_second
		sta  (P8ZP_SCRATCH_W1),y
		dec  _index_second
		dec  _index_second
		inc  _index_first
		inc  _index_first
		dec  _loop_count
		bne  _loop_hi

		rts
		.pend


func_peekw   .proc
	; -- read the word value on the address in AY
	sta  P8ZP_SCRATCH_W1
	sty  P8ZP_SCRATCH_W1+1
	ldy  #0
	lda  (P8ZP_SCRATCH_W1),y
	pha
	iny
	lda  (P8ZP_SCRATCH_W1),y
	tay
	pla
	rts
	.pend


func_pokew   .proc
	; -- store the word value in AY in the address in P8ZP_SCRATCH_W1
	sty  P8ZP_SCRATCH_REG
	ldy  #0
	sta  (P8ZP_SCRATCH_W1),y
	iny
	lda  P8ZP_SCRATCH_REG
	sta  (P8ZP_SCRATCH_W1),y
	rts
	.pend
