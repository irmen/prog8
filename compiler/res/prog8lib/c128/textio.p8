; Prog8 definitions for the Text I/O and Screen routines for the Commodore-64

%import syslib
%import conv


txt {

const ubyte DEFAULT_WIDTH = 40
const ubyte DEFAULT_HEIGHT = 25


sub  clear_screen() {
    txt.chrout(147)
}

sub home() {
    txt.chrout(19)
}

sub nl() {
    txt.chrout('\n')
}

sub spc() {
    txt.chrout(' ')
}

asmsub column(ubyte col @A) clobbers(A, X, Y) {
    ; ---- set the cursor on the given column (starting with 0) on the current line
    %asm {{
        sec
        jsr  c64.PLOT
        tay
        clc
        jmp  c64.PLOT
    }}
}

asmsub  fill_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  {
	; ---- fill the character screen with the given fill character and character color.
	;      (assumes screen and color matrix are at their default addresses)

	%asm {{
		pha
		tya
		jsr  clear_screencolors
		pla
		jsr  clear_screenchars
		rts
        }}

}

asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
		ldy  #250
-		sta  c64.Screen+250*0-1,y
		sta  c64.Screen+250*1-1,y
		sta  c64.Screen+250*2-1,y
		sta  c64.Screen+250*3-1,y
		dey
		bne  -
		rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
		ldy  #250
-		sta  c64.Colors+250*0-1,y
		sta  c64.Colors+250*1-1,y
		sta  c64.Colors+250*2-1,y
		sta  c64.Colors+250*3-1,y
		dey
		bne  -
		rts
        }}
}

sub color (ubyte txtcol) {
    c64.COLOR = txtcol
}

sub lowercase() {
    c64.VMCSB |= 2
    c128.VM1 |= 2
}

sub uppercase() {
    c64.VMCSB &= ~2
    c128.VM1 &= ~2
}

asmsub  scroll_left  (ubyte alsocolors @ Pc) clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too

	%asm {{
		stx  P8ZP_SCRATCH_REG
		bcc _scroll_screen

+               ; scroll the screen and the color memory
		ldx  #0
		ldy  #38
-
        .for row=0, row<=24, row+=1
            lda  c64.Screen + 40*row + 1,x
            sta  c64.Screen + 40*row + 0,x
            lda  c64.Colors + 40*row + 1,x
            sta  c64.Colors + 40*row + 0,x
        .next
		inx
		dey
		bpl  -
		rts

_scroll_screen  ; scroll only the screen memory
		ldx  #0
		ldy  #38
-
        .for row=0, row<=24, row+=1
            lda  c64.Screen + 40*row + 1,x
            sta  c64.Screen + 40*row + 0,x
        .next
		inx
		dey
		bpl  -

		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  scroll_right  (ubyte alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  P8ZP_SCRATCH_REG
		bcc  _scroll_screen

+               ; scroll the screen and the color memory
		ldx  #38
-
        .for row=0, row<=24, row+=1
            lda  c64.Screen + 40*row + 0,x
            sta  c64.Screen + 40*row + 1,x
            lda  c64.Colors + 40*row + 0,x
            sta  c64.Colors + 40*row + 1,x
        .next
		dex
		bpl  -
		rts

_scroll_screen  ; scroll only the screen memory
		ldx  #38
-
        .for row=0, row<=24, row+=1
            lda  c64.Screen + 40*row + 0,x
            sta  c64.Screen + 40*row + 1,x
        .next
		dex
		bpl  -

		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  scroll_up  (ubyte alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  P8ZP_SCRATCH_REG
		bcc  _scroll_screen

+               ; scroll the screen and the color memory
		ldx #39
-
        .for row=1, row<=24, row+=1
            lda  c64.Screen + 40*row,x
            sta  c64.Screen + 40*(row-1),x
            lda  c64.Colors + 40*row,x
            sta  c64.Colors + 40*(row-1),x
        .next
		dex
		bpl  -
		rts

_scroll_screen  ; scroll only the screen memory
		ldx #39
-
        .for row=1, row<=24, row+=1
            lda  c64.Screen + 40*row,x
            sta  c64.Screen + 40*(row-1),x
        .next
		dex
		bpl  -

		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  scroll_down  (ubyte alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  P8ZP_SCRATCH_REG
		bcc  _scroll_screen

+               ; scroll the screen and the color memory
		ldx #39
-
        .for row=23, row>=0, row-=1
            lda  c64.Colors + 40*row,x
            sta  c64.Colors + 40*(row+1),x
            lda  c64.Screen + 40*row,x
            sta  c64.Screen + 40*(row+1),x
        .next
		dex
		bpl  -
		rts

_scroll_screen  ; scroll only the screen memory
		ldx #39
-
        .for row=23, row>=0, row-=1
            lda  c64.Screen + 40*row,x
            sta  c64.Screen + 40*(row+1),x
        .next
		dex
		bpl  -

		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

romsub $FFD2 = chrout(ubyte char @ A)    ; for consistency. You can also use c64.CHROUT directly ofcourse.

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
		stx  P8ZP_SCRATCH_REG
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
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
		jsr  c64.CHROUT
+		pla
		jsr  conv.byte2decimal
		jmp  print_ub._print_byte_digits
	}}
}

asmsub  print_ubhex  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  conv.ubyte2hex
		jsr  c64.CHROUT
		tya
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  print_ubbin  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG
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
		ldx  P8ZP_SCRATCH_REG
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
	    stx  P8ZP_SCRATCH_REG
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  c64.CHROUT
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

asmsub  input_chars  (uword buffer @ AY) clobbers(A) -> ubyte @ Y  {
	; ---- Input a string (max. 80 chars) from the keyboard. Returns length in Y. (string is terminated with a 0 byte as well)
	;      It assumes the keyboard is selected as I/O channel!

	%asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0				; char counter = 0
-		jsr  c64.CHRIN
		cmp  #$0d			; return (ascii 13) pressed?
		beq  +				; yes, end.
		sta  (P8ZP_SCRATCH_W1),y	; else store char in buffer
		iny
		bne  -
+		lda  #0
		sta  (P8ZP_SCRATCH_W1),y	; finish string with 0 byte
		rts

	}}
}

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A, Y)  {
	; ---- sets the character in the screen matrix at the given position
	%asm {{
		pha
		tya
		asl  a
		tay
		lda  _screenrows+1,y
		sta  _mod+2
		txa
		clc
		adc  _screenrows,y
		sta  _mod+1
		bcc  +
		inc  _mod+2
+		pla
_mod		sta  $ffff		; modified
		rts

_screenrows	.word  $0400 + range(0, 1000, 40)
	}}
}

asmsub  getchr  (ubyte col @A, ubyte row @Y) clobbers(Y) -> ubyte @ A {
	; ---- get the character in the screen matrix at the given location
	%asm  {{
		pha
		tya
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _mod+2
		pla
		clc
		adc  setchr._screenrows,y
		sta  _mod+1
		bcc  _mod
		inc  _mod+2
_mod		lda  $ffff		; modified
		rts
	}}
}

asmsub  setclr  (ubyte col @X, ubyte row @Y, ubyte color @A) clobbers(A, Y)  {
	; ---- set the color in A on the screen matrix at the given position
	%asm {{
		pha
		tya
		asl  a
		tay
		lda  _colorrows+1,y
		sta  _mod+2
		txa
		clc
		adc  _colorrows,y
		sta  _mod+1
		bcc  +
		inc  _mod+2
+		pla
_mod		sta  $ffff		; modified
		rts

_colorrows	.word  $d800 + range(0, 1000, 40)
	}}
}

asmsub  getclr  (ubyte col @A, ubyte row @Y) clobbers(Y) -> ubyte @ A {
	; ---- get the color in the screen color matrix at the given location
	%asm  {{
		pha
		tya
		asl  a
		tay
		lda  setclr._colorrows+1,y
		sta  _mod+2
		pla
		clc
		adc  setclr._colorrows,y
		sta  _mod+1
		bcc  _mod
		inc  _mod+2
_mod		lda  $ffff		; modified
		rts
	}}
}

sub  setcc  (ubyte column, ubyte row, ubyte char, ubyte charcolor)  {
	; ---- set char+color at the given position on the screen
	%asm {{
		lda  row
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _charmod+2
		adc  #$d4
		sta  _colormod+2
		lda  setchr._screenrows,y
		clc
		adc  column
		sta  _charmod+1
		sta  _colormod+1
		bcc  +
		inc  _charmod+2
		inc  _colormod+2
+		lda  char
_charmod	sta  $ffff		; modified
		lda  charcolor
_colormod	sta  $ffff		; modified
		rts
	}}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ A) clobbers(A) {
	; ---- safe wrapper around PLOT kernal routine, to save the X register.
	%asm  {{
		stx  P8ZP_SCRATCH_REG
		tax
		clc
		jsr  c64.PLOT
		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub width() clobbers(X,Y) -> ubyte @A {
    ; -- returns the text screen width (number of columns)
    %asm {{
        jsr  c64.SCREEN
        txa
        rts
    }}
}

asmsub height() clobbers(X, Y) -> ubyte @A {
    ; -- returns the text screen height (number of rows)
    %asm {{
        jsr  c64.SCREEN
        tya
        rts
    }}
}

}
