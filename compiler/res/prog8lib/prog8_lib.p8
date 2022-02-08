; Internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

prog8_lib {
	%asminclude "library:prog8_lib.asm"
	%asminclude "library:prog8_funcs.asm"

	; to store intermediary expression results for return values:
	; NOTE: these variables can be used in the StatementReorderer and StatementOptimizer
	uword @zp  retval_interm_uw
	word  @zp  retval_interm_w
	ubyte @zp  retval_interm_ub
	byte  @zp  retval_interm_b
	word       retval_interm_w2
	byte       retval_interm_b2

        ; prog8 "hooks" to be able to access the temporary scratch variables
        ; YOU SHOULD NOT USE THESE IN USER CODE - THESE ARE MEANT FOR INTERNAL COMPILER USE
        ; NOTE: the assembly code generator will match these names and not generate
        ;       new variables/memdefs for them, rather, they'll point to the scratch variables directly.
        &ubyte P8ZP_SCRATCH_REG = $ff
        &byte P8ZP_SCRATCH_B1 = $ff
        &uword P8ZP_SCRATCH_W1 = $ff
        &word P8ZP_SCRATCH_W2 = $ff


	asmsub pattern_match(str string @AY, str pattern @R0) clobbers(Y) -> ubyte @A {
		%asm {{
; pattern matching of a string.
; Input:  cx16.r0:  A NUL-terminated, <255-length pattern
;              AY:  A NUL-terminated, <255-length string
;
; Output: A = 1 if the string matches the pattern, A = 0 if not.
;
; Notes:  Clobbers A, X, Y. Each * in the pattern uses 4 bytes of stack.
;
; see http://6502.org/source/strings/patmatch.htm

str = P8ZP_SCRATCH_W1

	stx  P8ZP_SCRATCH_REG
	sta  str
	sty  str+1
	lda  cx16.r0
	sta  modify_pattern1+1
	sta  modify_pattern2+1
	lda  cx16.r0+1
	sta  modify_pattern1+2
	sta  modify_pattern2+2
	jsr  _match
	lda  #0
	adc  #0
	ldx  P8ZP_SCRATCH_REG
	rts


_match
	ldx #$00        ; x is an index in the pattern
	ldy #$ff        ; y is an index in the string
modify_pattern1
next    lda $ffff,x   ; look at next pattern character    MODIFIED
	cmp #'*'     ; is it a star?
	beq star        ; yes, do the complicated stuff
	iny             ; no, let's look at the string
	cmp #'?'     ; is the pattern caracter a ques?
	bne reg         ; no, it's a regular character
	lda (str),y     ; yes, so it will match anything
	beq fail        ;  except the end of string
reg     cmp (str),y     ; are both characters the same?
	bne fail        ; no, so no match
	inx             ; yes, keep checking
	cmp #0          ; are we at end of string?
	bne next        ; not yet, loop
found   rts             ; success, return with c=1

star    inx             ; skip star in pattern
modify_pattern2
	cmp $ffff,x   	; string of stars equals one star	MODIFIED
	beq star        ;  so skip them also
stloop  txa             ; we first try to match with * = ""
	pha             ;  and grow it by 1 character every
	tya             ;  time we loop
	pha             ; save x and y on stack
	jsr next        ; recursive call
	pla             ; restore x and y
	tay
	pla
	tax
	bcs found       ; we found a match, return with c=1
	iny             ; no match yet, try to grow * string
	lda (str),y     ; are we at the end of string?
	bne stloop      ; not yet, add a character
fail    clc             ; yes, no match found, return with c=0
	rts
		}}
	}
}
