; Prog8 definitions for the Text I/O and Screen routines for the Commodore PET
; All routines work with Screencode character encoding, except `print`, `chrout` and `input_chars`,
; these work with PETSCII encoding instead.

%import syslib
%import conv
%import shared_cbm_textio_functions

txt {
    %option no_symbol_prefixing, ignore_unused

const ubyte DEFAULT_WIDTH = 40
const ubyte DEFAULT_HEIGHT = 25

extsub $FFD2 = chrout(ubyte character @ A)    ; for consistency. You can also use cbm.CHROUT directly ofcourse. Note: takes a PETSCII encoded character.


sub bell() {
    chrout(7)
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

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A, Y)  {
	; ---- sets the character in the screen matrix at the given position
	%asm {{
		pha
		tya
		asl  a
		tay
		lda  _screenrows+1,y
		sta  P8ZP_SCRATCH_W1+1
		txa
		clc
		adc  _screenrows,y
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		pla
        ldy  #0
		sta  (P8ZP_SCRATCH_W1),y
		rts

_screenrows	.word  cbm.Screen + range(0, 1000, 40)
        ; !notreached!
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
		sta  P8ZP_SCRATCH_W1+1
		pla
		clc
		adc  setchr._screenrows,y
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		rts
	}}
}

sub  setclr  (ubyte col, ubyte row, ubyte color)  {
    ; --- dummy function on PET
}


sub  setcc  (ubyte col, ubyte row, ubyte character, ubyte charcolor)  {
	; ---- set char at the given position on the screen. NOTE: charcolor is ignored on PET
	%asm {{
  		lda  row
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  P8ZP_SCRATCH_W1+1
		lda  setchr._screenrows,y
		clc
		adc  col
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		lda  character
		ldy  #0
		sta  (P8ZP_SCRATCH_W1),y
		rts
	}}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ X) {
	%asm  {{
		stx $d8
		txa
		asl a
		tax
		lda setchr._screenrows,x
		sta $c4
		lda setchr._screenrows+1,x
		sta $c5
		sty $c6
		rts
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

asmsub size() clobbers(A) -> ubyte @X, ubyte @Y {
    ; -- returns the text screen width in X and height in Y (number of columns and rows)
    %asm {{
        jsr  width
        tax
        jsr  height
        tay
        rts
    }}
}

asmsub waitkey() -> ubyte @A {
    %asm {{
-       jsr cbm.GETIN
        beq -
        rts
    }}
}
}
