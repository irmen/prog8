; Prog8 definitions for the Text I/O and Screen routines for the Atari 800XL

%import syslib
%import conv


txt {

    %option no_symbol_prefixing

const ubyte DEFAULT_WIDTH = 40
const ubyte DEFAULT_HEIGHT = 24


sub  clear_screen() {
    txt.chrout(125)
}

sub nl() {
    txt.chrout('\n')
}

sub spc() {
    txt.chrout(' ')
}

asmsub column(ubyte col @A) clobbers(A, X, Y) {
    ; ---- set the cursor on the given column (starting with 0) on the current line
    ;      TODO
    %asm {{
        rts
    }}
}

asmsub  fill_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  {
    ; ---- fill the character screen with the given fill character and character color.
    ;      (assumes screen and color matrix are at their default addresses)
    ;      TODO

    %asm {{
        rts
    }}
}

asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	; TODO
	%asm {{
	    rts
	}}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	; TODO
	%asm {{
	    rts
        }}
}

sub color (ubyte txtcol) {
    ; TODO
}

sub lowercase() {
    ; TODO
}

sub uppercase() {
    ; TODO
}

asmsub  scroll_left  (bool alsocolors @ Pc) clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	;  TODO

	%asm {{
	    rts
        }}
}

asmsub  scroll_right  (bool alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	; TODO
	%asm {{
	    rts
        }}
}

asmsub  scroll_up  (bool alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	; TODO
	%asm {{
	    rts
        }}
}

asmsub  scroll_down  (bool alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	; TODO
	%asm {{
	    rts
        }}
}


romsub $F2B0 = outchar(ubyte char @ A)
romsub $F2Fd = waitkey()

asmsub chrout(ubyte char @ A) {
	%asm {{
		sta  _tmp_outchar+1
		txa
		pha
		tya
		pha
_tmp_outchar
		lda  #0
		jsr  outchar
		pla
		tay
		pla
		tax
		rts
	}}
}

asmsub  print (str text @ AY) clobbers(A,Y)  {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to CHROUT of that single char.
	%asm {{
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  (P8ZP_SCRATCH_B1),y
		beq  +
		jsr  chrout
		iny
		bne  -
+		rts
	}}
}

asmsub  print_ub0  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		stx  P8ZP_SCRATCH_REG
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  chrout
		pla
		jsr  chrout
		txa
		jsr  chrout
		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		stx  P8ZP_SCRATCH_REG
		jsr  conv.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		beq  +
		tya
		jsr  chrout
		pla
		jsr  chrout
		jmp  _ones
+       pla
        cmp  #'0'
        beq  _ones
        jsr  chrout
_ones   txa
		jsr  chrout
		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,Y)  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		stx  P8ZP_SCRATCH_REG
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  chrout
+		pla
		jsr  conv.byte2decimal
		jmp  print_ub._print_byte_digits
	}}
}

asmsub  print_ubhex  (ubyte value @ A, bool prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG
		bcc  +
		pha
		lda  #'$'
		jsr  chrout
		pla
+		jsr  conv.ubyte2hex
		jsr  chrout
		tya
		jsr  chrout
		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  print_ubbin  (ubyte value @ A, bool prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG
		sta  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'%'
		jsr  chrout
+		ldy  #8
-		lda  #'0'
		asl  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'1'
+		jsr  chrout
		dey
		bne  -
		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  print_uwbin  (uword value @ AY, bool prefix @ Pc) clobbers(A,Y)  {
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

asmsub  print_uwhex  (uword value @ AY, bool prefix @ Pc) clobbers(A,Y)  {
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
	    stx  P8ZP_SCRATCH_REG
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  chrout
		iny
		bne  -
+		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
	    stx  P8ZP_SCRATCH_REG
		jsr  conv.uword2decimal
		ldx  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
		beq  _allzero
		cmp  #'0'
		bne  _gotdigit
		iny
		bne  -

_gotdigit
		jsr  chrout
		iny
		lda  conv.uword2decimal.decTenThousands,y
		bne  _gotdigit
		rts
_allzero
        lda  #'0'
        jmp  chrout
	}}
}

asmsub  print_w  (word value @ AY) clobbers(A,Y)  {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
	%asm {{
		cpy  #0
		bpl  +
		pha
		lda  #'-'
		jsr  chrout
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

asmsub  input_chars  (uword buffer @ AY) clobbers(A) -> ubyte @ Y  {
	; ---- Input a string (max. 80 chars) from the keyboard. Returns length in Y. (string is terminated with a 0 byte as well)
	;      It assumes the keyboard is selected as I/O channel!
	;  TODO

	%asm {{
	    ldy  #0
	    rts
	}}
}

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A, Y)  {
	; ---- sets the character in the screen matrix at the given position
	; TODO
	%asm {{
        	rts
        }}
}

asmsub  getchr  (ubyte col @A, ubyte row @Y) clobbers(Y) -> ubyte @ A {
	; ---- get the character in the screen matrix at the given location
	; TODO
	%asm {{
        	rts
        }}
}

asmsub  setclr  (ubyte col @X, ubyte row @Y, ubyte color @A) clobbers(A, Y)  {
	; ---- set the color in A on the screen matrix at the given position
	; TODO
	%asm {{
        	rts
        }}
}

asmsub  getclr  (ubyte col @A, ubyte row @Y) clobbers(Y) -> ubyte @ A {
	; ---- get the color in the screen color matrix at the given location
	; TODO
	%asm {{
        	rts
        }}
}

sub  setcc  (ubyte column, ubyte row, ubyte char, ubyte charcolor)  {
	; ---- set char+color at the given position on the screen
	; TODO
	%asm {{
        	rts
        }}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ A) clobbers(A) {
	; ---- set cursor at specific position
	; TODO
	%asm  {{
	    rts
	}}
}

asmsub width() clobbers(X,Y) -> ubyte @A {
    ; -- returns the text screen width (number of columns)
    ; TODO
    %asm {{
        lda  #0
        rts
    }}
}

asmsub height() clobbers(X, Y) -> ubyte @A {
    ; -- returns the text screen height (number of rows)
    ; TODO
    %asm {{
        lda  #0
        rts
    }}
}

}
