; Internal library routines - always included by the compiler
; Generic machine independent 6502 code.


orig_stackpointer	.byte  0	; stores the Stack pointer register at program start

reg_less_uw	.proc
		;  AY < P8ZP_SCRATCH_W2?
		cpy  P8ZP_SCRATCH_W2+1
		bcc  _true
		bne  _false
		cmp  P8ZP_SCRATCH_W2
		bcc  _true
_false		lda  #0
		rts
_true		lda  #1
		rts
		.pend

reg_less_w	.proc
		; -- AY < P8ZP_SCRATCH_W2?
		cmp  P8ZP_SCRATCH_W2
		tya
		sbc  P8ZP_SCRATCH_W2+1
		bvc  +
		eor  #$80
+		bmi  _true
		lda  #0
		rts
_true		lda  #1
		rts
		.pend

reg_lesseq_uw	.proc
		; AY <= P8ZP_SCRATCH_W2?
		cpy  P8ZP_SCRATCH_W2+1
		beq  +
		bcc  _true
		lda  #0
		rts
+		cmp  P8ZP_SCRATCH_W2
		bcc  _true
		beq  _true
		lda  #0
		rts
_true		lda  #1
		rts
		.pend

reg_lesseq_w	.proc
		; -- P8ZP_SCRATCH_W2 <= AY ?   (note: order different from other routines)
		cmp  P8ZP_SCRATCH_W2
		tya
		sbc  P8ZP_SCRATCH_W2+1
		bvc  +
		eor  #$80
+		bpl  +
		lda  #0
		rts
+		lda  #1
		rts
		.pend


memcopy16_up	.proc
	; -- copy memory UP from (P8ZP_SCRATCH_W1) to (P8ZP_SCRATCH_W2) of length X/Y (16-bit, X=lo, Y=hi)
	;    clobbers register A,X,Y
		source = P8ZP_SCRATCH_W1
		dest = P8ZP_SCRATCH_W2
		length = P8ZP_SCRATCH_B1   ; (and SCRATCH_ZPREG)

		stx  length
		sty  length+1

		ldx  length             ; move low byte of length into X
		bne  +                  ; jump to start if X > 0
		dec  length             ; subtract 1 from length
+		ldy  #0                 ; set Y to 0
-		lda  (source),y         ; set A to whatever (source) points to offset by Y
		sta  (dest),y           ; move A to location pointed to by (dest) offset by Y
		iny                     ; increment Y
		bne  +                  ; if Y<>0 then (rolled over) then still moving bytes
		inc  source+1           ; increment hi byte of source
		inc  dest+1             ; increment hi byte of dest
+		dex                     ; decrement X (lo byte counter)
		bne  -                  ; if X<>0 then move another byte
		dec  length             ; we've moved 255 bytes, dec length
		bpl  -                  ; if length is still positive go back and move more
		rts                     ; done
		.pend


memset          .proc
	; -- fill memory from (P8ZP_SCRATCH_W1), length XY, with value in A.
	;    clobbers X, Y
		stx  P8ZP_SCRATCH_B1
		sty  _save_reg
		ldy  #0
		ldx  _save_reg
		beq  _lastpage

_fullpage	sta  (P8ZP_SCRATCH_W1),y
		iny
		bne  _fullpage
		inc  P8ZP_SCRATCH_W1+1          ; next page
		dex
		bne  _fullpage

_lastpage	ldy  P8ZP_SCRATCH_B1
		beq  +
-         	dey
		sta  (P8ZP_SCRATCH_W1),y
		bne  -

+           	rts
_save_reg	.byte  0
		.pend


memsetw		.proc
	; -- fill memory from (P8ZP_SCRATCH_W1) number of words in P8ZP_SCRATCH_W2, with word value in AY.
	;    clobbers A, X, Y
		sta  _mod1+1                    ; self-modify
		sty  _mod1b+1                   ; self-modify
		sta  _mod2+1                    ; self-modify
		sty  _mod2b+1                   ; self-modify
		ldx  P8ZP_SCRATCH_W1
		stx  P8ZP_SCRATCH_B1
		ldx  P8ZP_SCRATCH_W1+1
		inx
		stx  P8ZP_SCRATCH_REG                ; second page

		ldy  #0
		ldx  P8ZP_SCRATCH_W2+1
		beq  _lastpage

_fullpage
_mod1           lda  #0                         ; self-modified
		sta  (P8ZP_SCRATCH_W1),y        ; first page
		sta  (P8ZP_SCRATCH_B1),y            ; second page
		iny
_mod1b		lda  #0                         ; self-modified
		sta  (P8ZP_SCRATCH_W1),y        ; first page
		sta  (P8ZP_SCRATCH_B1),y            ; second page
		iny
		bne  _fullpage
		inc  P8ZP_SCRATCH_W1+1          ; next page pair
		inc  P8ZP_SCRATCH_W1+1          ; next page pair
		inc  P8ZP_SCRATCH_B1+1              ; next page pair
		inc  P8ZP_SCRATCH_B1+1              ; next page pair
		dex
		bne  _fullpage

_lastpage	ldx  P8ZP_SCRATCH_W2
		beq  _done

		ldy  #0
-
_mod2           lda  #0                         ; self-modified
                sta  (P8ZP_SCRATCH_W1), y
		inc  P8ZP_SCRATCH_W1
		bne  _mod2b
		inc  P8ZP_SCRATCH_W1+1
_mod2b          lda  #0                         ; self-modified
		sta  (P8ZP_SCRATCH_W1), y
		inc  P8ZP_SCRATCH_W1
		bne  +
		inc  P8ZP_SCRATCH_W1+1
+               dex
		bne  -
_done		rts
		.pend



ror2_mem_ub	.proc
		; -- in-place 8-bit ror of byte at memory location in AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		bcc  +
		ora  #$80
+		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol2_mem_ub	.proc
		; -- in-place 8-bit rol of byte at memory location in AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #$80
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol_array_ub	.proc
		; -- rol a ubyte in an array
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  _arg_index
		lda  (P8ZP_SCRATCH_W1),y
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
_arg_target	.word	0
_arg_index	.byte   0
		.pend


ror_array_ub	.proc
		; -- ror a ubyte in an array
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  _arg_index
		lda  (P8ZP_SCRATCH_W1),y
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
_arg_target	.word	0
_arg_index	.byte   0
		.pend

ror2_array_ub	.proc
		; -- ror2 (8-bit ror) a ubyte in an array
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  _arg_index
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		bcc  +
		ora  #$80
+		sta  (P8ZP_SCRATCH_W1),y
		rts
_arg_target	.word	0
_arg_index	.byte   0
		.pend

rol2_array_ub	.proc
		; -- rol2 (8-bit rol) a ubyte in an array
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  _arg_index
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #$80
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
_arg_target	.word	0
_arg_index	.byte   0
		.pend

ror_array_uw	.proc
		; -- ror a uword in an array
		php
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  _arg_index
		asl  a
		tay
		iny
		lda  (P8ZP_SCRATCH_W1),y
		plp
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		dey
		lda  (P8ZP_SCRATCH_W1),y
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
_arg_target	.word  0
_arg_index	.byte  0
		.pend

rol_array_uw	.proc
		; -- rol a uword in an array
		php
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  _arg_index
		asl  a
		tay
		lda  (P8ZP_SCRATCH_W1),y
		plp
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
_arg_target	.word  0
_arg_index	.byte  0
		.pend

rol2_array_uw	.proc
		; -- rol2 (16-bit rol) a uword in an array
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  _arg_index
		asl  a
		tay
		lda  (P8ZP_SCRATCH_W1),y
		asl  a
		sta  (P8ZP_SCRATCH_W1),y
		iny
		lda  (P8ZP_SCRATCH_W1),y
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		bcc  +
		dey
		lda  (P8ZP_SCRATCH_W1),y
		adc  #0
		sta  (P8ZP_SCRATCH_W1),y
+		rts
_arg_target	.word  0
_arg_index	.byte  0
		.pend

ror2_array_uw	.proc
		; -- ror2 (16-bit ror) a uword in an array
		lda  _arg_target
		ldy  _arg_target+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  _arg_index
		asl  a
		tay
		iny
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		sta  (P8ZP_SCRATCH_W1),y
		dey
		lda  (P8ZP_SCRATCH_W1),y
		ror  a
		sta  (P8ZP_SCRATCH_W1),y
		bcc  +
		iny
		lda  (P8ZP_SCRATCH_W1),y
		ora  #$80
		sta  (P8ZP_SCRATCH_W1),y
+		rts
_arg_target	.word  0
_arg_index	.byte  0
		.pend


strcpy		.proc
		; copy a string (must be 0-terminated) from A/Y to (P8ZP_SCRATCH_W1)
		; it is assumed the target string is large enough.
		; returns the length of the string that was copied in Y.
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #$ff
-		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		bne  -
		rts
		.pend

strcmp_expression	.proc
		; -- compare strings, result in A
		lda  _arg_s2
		ldy  _arg_s2+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  _arg_s1
		ldy  _arg_s1+1
		jmp  strcmp_mem
_arg_s1		.word  0
_arg_s2		.word  0
		.pend

strcmp_mem	.proc
		; --   compares strings in s1 (AY) and s2 (P8ZP_SCRATCH_W2).
		;      Returns -1,0,1 in A, depeding on the ordering. Clobbers Y.
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
_loop		lda  (P8ZP_SCRATCH_W1),y
		bne  +
		lda  (P8ZP_SCRATCH_W2),y
		bne  _return_minusone
		beq  _return
+		cmp  (P8ZP_SCRATCH_W2),y
		bcc  _return_minusone
		bne  _return_one
		inc  P8ZP_SCRATCH_W1
		bne  +
		inc  P8ZP_SCRATCH_W1+1
+		inc  P8ZP_SCRATCH_W2
		bne  _loop
		inc  P8ZP_SCRATCH_W2+1
		bne  _loop
_return_one
		lda  #1
_return		rts
_return_minusone
		lda  #-1
		rts
		.pend


strlen          .proc
        ; -- returns the number of bytes in the string in AY, in Y.
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		iny
		bne  -
+		rts
		.pend


containment_bytearray	.proc
	; -- check if a value exists in a byte array.
	;    parameters: P8ZP_SCRATCH_W1: address of the byte array, A = byte to check, Y = length of array (>=1).
	;    returns boolean 0/1 in A.
		dey
-		cmp  (P8ZP_SCRATCH_W1),y
		beq  +
		dey
		cpy  #255
		bne  -
		lda  #0
		rts
+		lda  #1
		rts
		.pend

containment_wordarray	.proc
	; -- check if a value exists in a word array.
	;    parameters: P8ZP_SCRATCH_W1: value to check, P8ZP_SCRATCH_W2: address of the word array, Y = length of array (>=1).
	;    returns boolean 0/1 in A.
		dey
		tya
		asl  a
		tay
-		lda  P8ZP_SCRATCH_W1
		cmp  (P8ZP_SCRATCH_W2),y
		bne  +
		lda  P8ZP_SCRATCH_W1+1
		iny
		cmp  (P8ZP_SCRATCH_W2),y
		beq  _found
		dey
+		dey
		dey
		cpy  #254
		bne  -
		lda  #0
		rts
_found		lda  #1
		rts
		.pend
