; Prog8 definitions for the Text I/O and Screen routines for the Commodore PET

%import syslib
%import conv


txt {
    %option no_symbol_prefixing

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



asmsub get_column() -> ubyte @Y {
    %asm {{
        sec
        jmp cbm.PLOT
    }}
}

asmsub row(ubyte rownum @A) clobbers(A, X, Y) {
    ; ---- set the cursor on the given row (starting with 0) on the current line
    %asm {{
        sec
        jsr  cbm.PLOT
        tax
        clc
        jmp  cbm.PLOT
    }}
}

asmsub get_row() -> ubyte @X {
    %asm {{
        sec
        jmp cbm.PLOT
    }}
}

sub get_cursor(uword colptr, uword rowptr) {
    %asm {{
        sec
        jsr cbm.PLOT
        tya
        ldy #$00
        sta (colptr),y
        txa
        sta (rowptr),y
    }}
}


asmsub  fill_screen (ubyte character @ A, ubyte color @ Y) clobbers(A)  {
	; ---- fill the character screen with the given fill character. color is ignored on PET
	%asm {{
		jmp  clear_screenchars
        }}

}

asmsub  clear_screenchars (ubyte character @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character
	;      (assumes screen matrix is at the default address)
	%asm {{
		ldy  #250
-		sta  cbm.Screen+250*0-1,y
		sta  cbm.Screen+250*1-1,y
		sta  cbm.Screen+250*2-1,y
		sta  cbm.Screen+250*3-1,y
		dey
		bne  -
		rts
        }}
}

sub  clear_screencolors (ubyte color)  {
    ; --- dummy function on PET
}

sub color (ubyte txtcol) {
    ; --- dummy function on PET
}


sub lowercase() {
    txt.chrout(14)
}

sub uppercase() {
    txt.chrout(142)
}

asmsub  scroll_left  () clobbers(A, X, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself

	%asm {{
		ldx  #0
		ldy  #38
-
        .for row=0, row<=24, row+=1
            lda  cbm.Screen + 40*row + 1,x
            sta  cbm.Screen + 40*row + 0,x
        .next
		inx
		dey
		bpl  -

		rts
	}}
}

asmsub  scroll_right  () clobbers(A,X)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	%asm {{
		ldx  #38
-
        .for row=0, row<=24, row+=1
            lda  cbm.Screen + 40*row + 0,x
            sta  cbm.Screen + 40*row + 1,x
        .next
		dex
		bpl  -

		rts
	}}
}

asmsub  scroll_up  () clobbers(A,X)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	%asm {{
		ldx #39
-
        .for row=1, row<=24, row+=1
            lda  cbm.Screen + 40*row,x
            sta  cbm.Screen + 40*(row-1),x
        .next
		dex
		bpl  -

		rts
	}}
}

asmsub  scroll_down  () clobbers(A,X)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	%asm {{
		ldx #39
-
        .for row=23, row>=0, row-=1
            lda  cbm.Screen + 40*row,x
            sta  cbm.Screen + 40*(row+1),x
        .next
		dex
		bpl  -

		rts
	}}
}

romsub $FFD2 = chrout(ubyte character @ A)    ; for consistency. You can also use cbm.CHROUT directly ofcourse.

asmsub  print (str text @ AY) clobbers(A,Y)  {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to cbm.CHROUT of that single char.
	%asm {{
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  (P8ZP_SCRATCH_B1),y
		beq  +
		jsr  cbm.CHROUT
		iny
		bne  -
+		rts
	}}
}

asmsub  print_ub0  (ubyte value @ A) clobbers(A,X,Y)  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  cbm.CHROUT
		pla
		jsr  cbm.CHROUT
		txa
		jmp  cbm.CHROUT
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,X,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		jsr  conv.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		beq  +
		tya
		jsr  cbm.CHROUT
		pla
		jsr  cbm.CHROUT
		jmp  _ones
+       pla
        cmp  #'0'
        beq  _ones
        jsr  cbm.CHROUT
_ones   txa
		jmp  cbm.CHROUT
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,X,Y)  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  cbm.CHROUT
+		pla
		jsr  conv.byte2decimal
		jmp  print_ub._print_byte_digits
	}}
}

asmsub  print_ubhex  (ubyte value @ A, bool prefix @ Pc) clobbers(A,X,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		bcc  +
		pha
		lda  #'$'
		jsr  cbm.CHROUT
		pla
+		jsr  conv.ubyte2hex
		jsr  cbm.CHROUT
		tya
		jmp  cbm.CHROUT
	}}
}

asmsub  print_ubbin  (ubyte value @ A, bool prefix @ Pc) clobbers(A,X,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		sta  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'%'
		jsr  cbm.CHROUT
+		ldy  #8
-		lda  #'0'
		asl  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'1'
+		jsr  cbm.CHROUT
		dey
		bne  -
		rts
	}}
}

asmsub  print_uwbin  (uword value @ AY, bool prefix @ Pc) clobbers(A,X,Y)  {
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

asmsub  print_uwhex  (uword value @ AY, bool prefix @ Pc) clobbers(A,X,Y)  {
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

asmsub  print_uw0  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
	%asm {{
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  cbm.CHROUT
		iny
		bne  -
+		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
		beq  _allzero
		cmp  #'0'
		bne  _gotdigit
		iny
		bne  -

_gotdigit
		jsr  cbm.CHROUT
		iny
		lda  conv.uword2decimal.decTenThousands,y
		bne  _gotdigit
		rts
_allzero
        lda  #'0'
        jmp  cbm.CHROUT
	}}
}

asmsub  print_w  (word value @ AY) clobbers(A,X,Y)  {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
	%asm {{
		cpy  #0
		bpl  +
		pha
		lda  #'-'
		jsr  cbm.CHROUT
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
-		jsr  cbm.CHRIN
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

_screenrows	.word  cbm.Screen + range(0, 1000, 40)
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

sub  setclr  (ubyte col, ubyte row, ubyte color)  {
    ; --- dummy function on PET
}


sub  setcc  (ubyte col, ubyte row, ubyte character, ubyte charcolor)  {
	; ---- set char at the given position on the screen. charcolor is ignored on PET
	%asm {{
  		lda  row
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _charmod+2
		lda  setchr._screenrows,y
		clc
		adc  col
		sta  _charmod+1
		bcc  +
		inc  _charmod+2
+		lda  character
_charmod	sta  $ffff		; modified
		rts
	}}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ X) {
	%asm  {{
	    jsr  home
	    cpy  #0
	    beq  +
-	    lda  #17
	    jsr  chrout
	    dey
	    bne  -
+	    cpx  #0
	    beq  +
-       lda  #29
        jsr  chrout
        dex
        bne  -
+		rts
	}}
}

asmsub width() clobbers(X,Y) -> ubyte @A {
    ; -- returns the text screen width (number of columns)
    %asm {{
        lda  $d5
        clc
        adc  #1
        rts
    }}
}

asmsub height() clobbers(X, Y) -> ubyte @A {
    ; -- returns the text screen height (number of rows)
    %asm {{
        lda  #25
        rts
    }}
}

}
