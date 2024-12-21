; utility debug code to print the SP (cpu stack pointer) register.

%import textio

test_stack {
    %option ignore_unused

    asmsub test() {
        %asm {{
	lda  #13
	jsr  txt.chrout
	lda  #'-'
	ldy  #12
-	jsr  txt.chrout
	dey
	bne  -
	lda  #13
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
	rts
        }}
    }
}
