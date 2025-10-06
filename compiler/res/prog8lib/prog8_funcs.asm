; ---- builtin functions

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

abs_l_into_R0R1	.proc
	; -- R0:R1 = abs(R0:R1)
    lda  cx16.r1H
    bmi  +   ; Negative if high bit of highest byte is set
    rts
+
    ; Invert all four bytes
    lda  cx16.r0L
    eor  #$FF
    sta  cx16.r0L
    lda  cx16.r0H
    eor  #$FF
    sta  cx16.r0H
    lda  cx16.r1L
    eor  #$FF
    sta  cx16.r1L
    lda  cx16.r1H
    eor  #$FF
    sta  cx16.r1H
    ; Add 1 to whole 32-bit value
    inc  cx16.r0L
    bne  +
    inc  cx16.r0H
    bne  +
    inc  cx16.r1L
    bne  +
    inc  cx16.r1H
+   rts
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

func_sign_ub_into_A	.proc
		cmp  #0
		bne  _pos
		rts
_pos		lda  #1
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


func_sqrt16_into_A	.proc
		; integer square root
		; http://6502org.wikidot.com/software-math-sqrt
		; https://github.com/TobyLobster/sqrt_test/blob/main/sqrt/sqrt7.a
		; Tweaked by TobyLobster and 0xC0DE to be smaller and faster
_numl = P8ZP_SCRATCH_W1
_numh = P8ZP_SCRATCH_W1+1
_loop_counter = P8ZP_SCRATCH_REG
_root = P8ZP_SCRATCH_B1
            sta  _numl
            sty  _numh
            ldx  #$ff
            stx  _loop_counter
            inx
            stx  _root
            sec
_loop       lda  _numh
            sbc  #$40
            tay
            txa
            sbc  _root
            bcc  +
            sty  _numh
            bcs  ++
+           txa
+           rol  _root
            asl  _numl
            rol  _numh
            rol  a
            asl  _numl
            rol  _numh
            rol  a
            tax
            lsr  _loop_counter
            bne  _loop
            lda  _root
            rts
		.pend


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
		.section BSS
_work1	.byte  ?
_work3	.word  ?
		.send BSS
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
		.section BSS
_work1	.byte  ?
_work3	.word  ?
		.send BSS
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


func_peek   .proc
	; -- read the byte value on the address in AY, into A
	sta  P8ZP_SCRATCH_W1
	sty  P8ZP_SCRATCH_W1+1
	ldy  #0
	lda  (P8ZP_SCRATCH_W1),y
	rts
	.pend

func_peekw   .proc
	; -- read the word value on the address in AY, into AY
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

func_peekl   .proc
	; -- read the ;pmg value on the address in AY, into R0:R1
	sta  P8ZP_SCRATCH_W1
	sty  P8ZP_SCRATCH_W1+1
	ldy  #0
	lda  (P8ZP_SCRATCH_W1),y
	sta  cx16.r0
	iny
	lda  (P8ZP_SCRATCH_W1),y
	sta  cx16.r0+1
	iny
	lda  (P8ZP_SCRATCH_W1),y
	sta  cx16.r0+2
	iny
	lda  (P8ZP_SCRATCH_W1),y
	sta  cx16.r0+3
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

func_pokel   .proc
	; -- store the long value in R0:R1 in the address in AY
	ldy  #0
	lda  cx16.r0
	sta  (P8ZP_SCRATCH_W1),y
	iny
	lda  cx16.r0+1
	sta  (P8ZP_SCRATCH_W1),y
	iny
	lda  cx16.r0+2
	sta  (P8ZP_SCRATCH_W1),y
	iny
	lda  cx16.r0+3
	sta  (P8ZP_SCRATCH_W1),y
	rts
	.pend

func_clamp_byte .proc
	; signed value in A, result in A
	; minimum in P8ZP_SCRATCH_W1
	; maximum in P8ZP_SCRATCH_W1+1
	tay
	sec
	sbc  P8ZP_SCRATCH_W1+1
	bvc  +
	eor  #$80
+       bmi  +
	lda  P8ZP_SCRATCH_W1+1
	tay
	jmp  ++
+       tya
+	sec
	sbc  P8ZP_SCRATCH_W1
	bvc  +
	eor  #$80
+       bmi  +
	tya
	rts
+       lda  P8ZP_SCRATCH_W1
	rts
	.pend


func_clamp_ubyte .proc
	; value in A, result in A
	; minimum in P8ZP_SCRATCH_W1
	; maximum in P8ZP_SCRATCH_W1+1
	cmp  P8ZP_SCRATCH_W1+1
	bcc  +
	lda  P8ZP_SCRATCH_W1+1
+       cmp  P8ZP_SCRATCH_W1
	bcc  +
	rts
+       lda  P8ZP_SCRATCH_W1
	rts
	.pend

func_clamp_word .proc
	; signed value in AY, result in AY
	; minimum in P8ZP_SCRATCH_W1
	; maximum in P8ZP_SCRATCH_W2
	sta  P8ZP_SCRATCH_B1
	sty  P8ZP_SCRATCH_REG
	ldy  P8ZP_SCRATCH_W2+1
	lda  P8ZP_SCRATCH_W2
	cmp  P8ZP_SCRATCH_B1
	tya
	sbc  P8ZP_SCRATCH_REG
	bvc  +
	eor  #$80
+       bpl  +
	lda  P8ZP_SCRATCH_W2
	ldy  P8ZP_SCRATCH_W2+1
	sta  P8ZP_SCRATCH_B1
	sty  P8ZP_SCRATCH_REG
+	ldy  P8ZP_SCRATCH_W1+1
	lda  P8ZP_SCRATCH_W1
	cmp  P8ZP_SCRATCH_B1
	tya
	sbc  P8ZP_SCRATCH_REG
	bvc  +
	eor  #$80
+       bpl  +
	ldy  P8ZP_SCRATCH_REG
	lda  P8ZP_SCRATCH_B1
	rts
+	ldy  P8ZP_SCRATCH_W1+1
	lda  P8ZP_SCRATCH_W1
	rts
	.pend

func_clamp_uword .proc
	; value in AY, result in AY
	; minimum in P8ZP_SCRATCH_W1
	; maximum in P8ZP_SCRATCH_W2
	sta  P8ZP_SCRATCH_B1
	sty  P8ZP_SCRATCH_REG
	cpy  P8ZP_SCRATCH_W2+1
	bcc  ++
	bne  +
	cmp  P8ZP_SCRATCH_W2
	bcc  ++
+       beq  +
	lda  P8ZP_SCRATCH_W2
	ldy  P8ZP_SCRATCH_W2+1
	sta  P8ZP_SCRATCH_B1
	sty  P8ZP_SCRATCH_REG
+	ldy  P8ZP_SCRATCH_REG
	lda  P8ZP_SCRATCH_B1
	cpy  P8ZP_SCRATCH_W1+1
	bcc  ++
	bne  +
	cmp  P8ZP_SCRATCH_W1
	bcc  ++
+       beq  +
	ldy  P8ZP_SCRATCH_REG
	lda  P8ZP_SCRATCH_B1
	rts
+	ldy  P8ZP_SCRATCH_W1+1
	lda  P8ZP_SCRATCH_W1
	rts

	.pend
