
.cpu  '6502'

CHROUT = $FFD2

; C64 addresses for float routines:
;MOVFM = $bba2
;CONUPK = $ba8c
;MOVAF = $bc0c
;MOVMF = $bbd4
;FOUT = $bddd
;FADDT = $b86a
;FMULTT = $ba2b
; CX16 addresses for float routines:
MOVFM = $fe63
CONUPK = $fe5a
MOVAF = $fe6c
MOVMF = $fe66
FOUT = $fe06
FADDT = $fe1b
FMULTT = $fe21

* = $0801

; 10 SYS 2062
	.word  (+), 10
	.null  $9e, " 2062"
+	.word  0

; program starts here at address 2062
	lda  #$8d
	jsr  CHROUT

	; calculate: value1*one + value2*one
	; (yes the multiplications with one do nothing value-wise, but they influence the routines being called...)
	lda  #<prog8_float_const_0	; -0.8
	ldy  #>prog8_float_const_0
	jsr  MOVFM			; FAC1 = -0.8
	lda  #<float_const_one		; 1.0
	ldy  #>float_const_one
	jsr  CONUPK			; ARG = 1.0
	jsr  FMULTT			; FAC1 = -0.8*1.0 = -0.8
	jsr  pushFAC1			; save value...
	lda  #<prog8_float_const_1	; 0.3
	ldy  #>prog8_float_const_1
	jsr  MOVFM			; FAC1 = 0.3
	lda  #<float_const_one		; 1.0
	ldy  #>float_const_one
	jsr  CONUPK			; ARG = 1.0
	jsr  FMULTT			; FAC1 = 0.3*1.0 = 0.3
	jsr  MOVAF			; ARG = FAC1 = 0.3
	jsr  popFAC1			; restore value... (-0.8)
	jsr  FADDT			; FAC1 = -0.8 + 0.3 = -0.5
	; ... result in FAC1 will be wrong on C64 (1.1 instead of -0.5)....

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
+	rts

string_1
	.text "-.5 was expected",$8d,0


floats_temp_var         .byte  0,0,0,0,0        ; temporary storage for a float

pushFAC1
	;-- push floating point in FAC onto the cpu stack
	; save return address
	pla
	sta  returnaddr
	pla
	sta  returnaddr+1
	ldx  #<floats_temp_var
	ldy  #>floats_temp_var
	jsr  MOVMF
	lda  floats_temp_var
	pha
	lda  floats_temp_var+1
	pha
	lda  floats_temp_var+2
	pha
	lda  floats_temp_var+3
	pha
	lda  floats_temp_var+4
	pha
	; re-push return address
	lda  returnaddr+1
	pha
	lda  returnaddr
	pha
	rts

returnaddr  .word 0

popFAC1
	; -- pop floating point value from cpu stack into FAC1
	; save return address
	pla
	sta  returnaddr
	pla
	sta  returnaddr+1
	pla
	sta  floats_temp_var+4
	pla
	sta  floats_temp_var+3
	pla
	sta  floats_temp_var+2
	pla
	sta  floats_temp_var+1
	pla
	sta  floats_temp_var
	lda  #<floats_temp_var
	ldy  #>floats_temp_var
	jsr  MOVFM
	; re-push return address
	lda  returnaddr+1
	pha
	lda  returnaddr
	pha
	rts


; global float constants
prog8_float_const_0	.byte  $80, $cc, $cc, $cc, $cc  ; float -0.8
prog8_float_const_1	.byte  $7f, $19, $99, $99, $99  ; float 0.3
float_const_one	.byte  $81, $00, $00, $00, $00  ; float 1.0
