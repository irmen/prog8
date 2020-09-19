; Prog8 definitions for the Text I/O and Screen routines for the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target cx16
%import cx16lib
%import conv


txt {

sub  clear_screen() {
    c64.CHROUT(147)         ; clear screen (spaces)
}


asmsub  fill_screen (ubyte char @ A, ubyte txtcolor @ Y) clobbers(A)  {
	; ---- fill the character screen with the given fill character and character color.
	%asm {{
        sta  P8ZP_SCRATCH_W1        ; fillchar
        sty  P8ZP_SCRATCH_W1+1      ; textcolor
        phx
        jsr  c64.SCREEN             ; get dimensions in X/Y
        dex
        dey
        txa
        asl  a
        adc  #1
        sta  P8ZP_SCRATCH_B1
-       ldx  P8ZP_SCRATCH_B1
-       stz  cx16.VERA_ADDR_H
        stx  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M
        lda  cx16.VERA_DATA0
        and  #$f0
        ora  P8ZP_SCRATCH_W1+1
        sta  cx16.VERA_DATA0
        dex
        stz  cx16.VERA_ADDR_H
        stx  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M
        lda  P8ZP_SCRATCH_W1
        sta  cx16.VERA_DATA0
        dex
        cpx  #255
        bne  -
        dey
        bpl  --
        plx
        rts
    }}

}

asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
        pha
        phx
        jsr  c64.SCREEN             ; get dimensions in X/Y
        dex
        dey
        txa
        asl  a
        sta  P8ZP_SCRATCH_B1
        pla
-       ldx  P8ZP_SCRATCH_B1
-       stz  cx16.VERA_ADDR_H
        stx  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M
        sta  cx16.VERA_DATA0
        dex
        dex
        cpx  #254
        bne  -
        dey
        bpl  --
        plx
        rts
        }}
}


ubyte[16] color_to_charcode = [$90,$05,$1c,$9f,$9c,$1e,$1f,$9e,$81,$95,$96,$97,$98,$99,$9a,$9b]

sub color (ubyte txtcol) {
    c64.CHROUT(color_to_charcode[txtcol & 15])
}

sub color2 (ubyte txtcol, ubyte bgcol) {
    c64.CHROUT(color_to_charcode[bgcol & 15])
    c64.CHROUT(1)       ; switch fg and bg colors
    c64.CHROUT(color_to_charcode[txtcol & 15])
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
		phx
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		plx
		rts
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		phx
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
		plx
		rts
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,Y)  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		phx
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  c64.CHROUT
+		pla
		jsr  conv.byte2decimal
		jmp  print_ub._print_byte_digits
	}}
}

asmsub  print_ubhex  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		phx
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  conv.ubyte2hex
		jsr  c64.CHROUT
		tya
		jsr  c64.CHROUT
		plx
		rts
	}}
}

asmsub  print_ubbin  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		phx
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
		plx
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
	    phx
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		plx
		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
	    phx
		jsr  conv.uword2decimal
		plx
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

; TODO implement the "missing" txtio subroutines: input_chars, setchr, getchr, setclr, getclr, scroll_left_full, (also right, up, down)

sub  setcc  (ubyte column, ubyte row, ubyte char, ubyte charcolor)  {
	; ---- set char+color at the given position on the screen
	%asm {{
	    phx
		lda  column
		asl  a
		tax
		ldy  row
		lda  charcolor
		and  #$0f
	    sta  P8ZP_SCRATCH_B1
        stz  cx16.VERA_ADDR_H
        stx  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M
        lda  char
        sta  cx16.VERA_DATA0
        inx
        stz  cx16.VERA_ADDR_H
        stx  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M
        lda  cx16.VERA_DATA0
        and  #$f0
        ora  P8ZP_SCRATCH_B1
        sta  cx16.VERA_DATA0
		plx
		rts
    }}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ A) clobbers(A) {
	; ---- safe wrapper around PLOT kernel routine, to save the X register.
	%asm  {{
		phx
		tax
		clc
		jsr  c64.PLOT
		plx
		rts
	}}
}

}
