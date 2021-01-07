; utility debug code to print the X (evalstack) and SP (cpu stack) registers.

%import textio

test_stack {

    asmsub test() {
        %asm {{
	stx  _saveX
	lda  #13
	jsr  txt.chrout
	lda  #'-'
	ldy  #12
-	jsr  txt.chrout
	dey
	bne  -
	lda  #13
	jsr  txt.chrout
	lda  #'x'
	jsr  txt.chrout
	lda  #'='
	jsr  txt.chrout
	lda  _saveX
	jsr  txt.print_ub
	lda  #' '
	jsr  txt.chrout
	lda  #'s'
	jsr  txt.chrout
	lda  #'p'
	jsr  txt.chrout
	lda  #'='
	jsr  txt.chrout
	tsx
	txa
	jsr  txt.print_ub
	lda  #13
	jsr  txt.chrout
	lda  #'-'
	ldy  #12
-	jsr  txt.chrout
	dey
	bne  -
	lda  #13
	jsr  txt.chrout
	ldx  _saveX
	rts
_saveX	.byte 0
        }}
    }
}
