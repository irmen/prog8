; Prog8 definitions for the Text I/O and Screen routines for the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


%import cx16lib
%import conv


txt {

asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  {
	; ---- clear the character screen with the given fill character and character color.
	;      (assumes screen and color matrix are at their default addresses)

	%asm {{
	    brk     ; TODO
        }}

}

asmsub  print (str text @ AY) clobbers(A,Y)  {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to c64.CHROUT of that single char.
	%asm {{
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  (P8ZP_SCRATCH_B1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		rts
	}}
}

asmsub  print_ub0  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		jsr  conv.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		beq  +
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		jmp  _ones
+       pla
        cmp  #'0'
        beq  _ones
        jsr  c64.CHROUT
_ones   txa
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,Y)  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  c64.CHROUT
+		pla
		jsr  conv.byte2decimal
		jsr  print_ub._print_byte_digits
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_ubhex  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  conv.ubyte2hex
		jsr  c64.CHROUT
		tya
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_ubbin  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		sta  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'%'
		jsr  c64.CHROUT
+		ldy  #8
-		lda  #'0'
		asl  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'1'
+		jsr  c64.CHROUT
		dey
		bne  -
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_uwbin  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the uword in A/Y in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		pha
		tya
		jsr  print_ubbin
		pla
		clc
		jmp  print_ubbin
	}}
}

asmsub  print_uwhex  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the uword in A/Y in hexadecimal form (4 digits)
	;      (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		pha
		tya
		jsr  print_ubhex
		pla
		clc
		jmp  print_ubhex
	}}
}

asmsub  print_uw0  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
	%asm {{
	    stx  P8ZP_SCRATCH_REG_X
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
	    stx  P8ZP_SCRATCH_REG_X
		jsr  conv.uword2decimal
		ldx  P8ZP_SCRATCH_REG_X
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
		beq  _allzero
		cmp  #'0'
		bne  _gotdigit
		iny
		bne  -

_gotdigit
		jsr  c64.CHROUT
		iny
		lda  conv.uword2decimal.decTenThousands,y
		bne  _gotdigit
		rts
_allzero
        lda  #'0'
        jmp  c64.CHROUT
	}}
}

asmsub  print_w  (word value @ AY) clobbers(A,Y)  {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
	%asm {{
		cpy  #0
		bpl  +
		pha
		lda  #'-'
		jsr  c64.CHROUT
		tya
		eor  #255
		tay
		pla
		eor  #255
		clc
		adc  #1
		bcc  +
		iny
+		jmp  print_uw
	}}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ A) clobbers(A) {
	; ---- safe wrapper around PLOT kernel routine, to save the X register.
	%asm  {{
		stx  P8ZP_SCRATCH_REG_X
		tax
		clc
		jsr  c64.PLOT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

}
