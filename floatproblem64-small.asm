
.cpu  '6502'

CHROUT = $FFD2

; C64 addresses for float routines:
MOVFM = $bba2
CONUPK = $ba8c
MOVAF = $bc0c
MOVEF = $bc0f
MOVMF = $bbd4
FOUT = $bddd
FADDT = $b86a
FMULTT = $ba2b
; CX16 addresses for float routines:
;MOVFM = $fe63
;CONUPK = $fe5a
;MOVAF = $fe6c
;MOVEF = $fe81
;MOVMF = $fe66
;FOUT = $fe06
;FADDT = $fe1b
;FMULTT = $fe21

* = $0801

; 10 SYS 2062
	.word  (+), 10
	.null  $9e, " 2062"
+	.word  0

; program starts here at address 2062
	lda  #$8d
	jsr  CHROUT

	ldx  #0
	stx  $6f
	lda  #<prog8_float_const_1	; 0.3
	ldy  #>prog8_float_const_1
	jsr  CONUPK			; ARG = 0.3
	lda  #<prog8_float_const_0	; -0.8
	ldy  #>prog8_float_const_0
	jsr  MOVFM			; FAC1 = -0.8				; On C64 this also seems to corrupt the value in ARG!!! (or at least, the sign of that)
	jsr  FADDT			; FAC1 = -0.8 + 0.3 = -0.5
	; ... result in FAC1 will be wrong on C64 (1.1 instead of -0.5)....
	;   Seems like MOVFM (loading FAC) corrupts the sign of ARG, so CONUPK (loading ARG) needs to be done AFTER MOVFM (loading FAC)...?

	; print FAC1
	jsr  FOUT		; fac1 to string in A/Y
	sta  _mod+1
	sty  _mod+2
	ldy  #0
_mod	lda  $ffff,y		; modified
	beq  +
	jsr  CHROUT
	iny
	bne  _mod
+	lda  #$8d
	jsr  CHROUT

	; print expected
	ldy #0
-	lda  string_1,y
	beq  +
	jsr  CHROUT
	iny
	bne  -
+

loop	jmp loop


string_1
	.text "-.5 was expected",$8d,0


; global float constants
prog8_float_const_0	.byte  $80, $cc, $cc, $cc, $cc  ; float -0.8
prog8_float_const_1	.byte  $7f, $19, $99, $99, $99  ; float 0.3
float_const_one	.byte  $81, $00, $00, $00, $00  ; float 1.0
