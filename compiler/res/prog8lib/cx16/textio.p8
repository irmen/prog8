; Prog8 definitions for the Text I/O and Screen routines for the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target cx16
%import syslib
%import conv


txt {

const ubyte DEFAULT_WIDTH = 80
const ubyte DEFAULT_HEIGHT = 60


sub  clear_screen() {
    clear_screenchars(' ')
}

asmsub  fill_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  {
	; ---- fill the character screen with the given fill character and character color.
	%asm {{
	    sty  _ly+1
        phx
        pha
        jsr  c64.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00010000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.
        stz  cx16.VERA_ADDR_L       ; start at (0,0)
        stz  cx16.VERA_ADDR_M
        pla
_lx     ldx  #0                     ; modified
        phy
_ly     ldy  #1                     ; modified
-       sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        dex
        bne  -
        ply
        dey
        beq  +
        stz  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M       ; next line
        bra  _lx
+       plx
        rts
        }}
}

asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
        phx
        pha
        jsr  c64.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00100000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 2, bank 0.
        stz  cx16.VERA_ADDR_L       ; start at (0,0)
        stz  cx16.VERA_ADDR_M
        pla
_lx     ldx  #0                     ; modified
-       sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        dex
        bne  -
        dey
        beq  +
        stz  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M       ; next line
        bra  _lx
+       plx
        rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
        phx
        sta  _la+1
        jsr  c64.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00100000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 2, bank 0.
        lda  #1
        sta  cx16.VERA_ADDR_L       ; start at (1,0)
        stz  cx16.VERA_ADDR_M
_lx     ldx  #0                     ; modified
_la     lda  #0                     ; modified
-       sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        dex
        bne  -
        dey
        beq  +
        lda  #1
        sta  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M       ; next line
        bra  _lx
+       plx
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

sub lowercase() {
    cx16.screen_set_charset(3, 0)  ; lowercase charset
}

sub uppercase() {
    cx16.screen_set_charset(2, 0)  ; uppercase charset
}

; TODO implement the "missing" txtio scroll subroutines:  scroll_left, scroll_right, scroll_down

asmsub  scroll_left  (ubyte dummy @ Pc) clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	;      Carry flag is a dummy on the cx16
	%asm {{
	    phx
	    jsr  c64.SCREEN
	    dex
        stx  _nextrow+1   ; number of columns to scroll
        dey
        sty  P8ZP_SCRATCH_B1    ; number of rows to scroll
        stz  cx16.VERA_CTRL
        stz  cx16.VERA_ADDR_H

_nextrow
        ldx  #0     ; modified
        stz  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M

-       lda  cx16.VERA_DATA0    ; char
        inc  cx16.VERA_ADDR_L
        ldy  cx16.VERA_DATA0    ; color
        dec  cx16.VERA_ADDR_L
        dec  cx16.VERA_ADDR_L
        dec  cx16.VERA_ADDR_L
        sta  cx16.VERA_DATA0
        inc  cx16.VERA_ADDR_L
        sty  cx16.VERA_DATA0
        inc  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_L
        dex
        bpl  -
        dec  P8ZP_SCRATCH_B1
        ldy  P8ZP_SCRATCH_B1
        bpl  _nextrow
	    plx
	    rts
	}}
}

asmsub  scroll_right  (ubyte dummy @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	;      Carry flag is a dummy on the cx16
}

asmsub  scroll_up  (ubyte dummy @ Pc) clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	;      Carry flag is a dummy on the cx16
	; TODO use the VERA dual data port
	%asm {{
	    phx
	    jsr  c64.SCREEN
	    txa
	    lsr  a
	    lsr  a
	    dea
	    sta  P8ZP_SCRATCH_REG       ; number of columns / 4 due to loop unroll
	    dey
	    sty  P8ZP_SCRATCH_B1        ; number of lines to scroll up
        stz  cx16.VERA_CTRL
        lda  #%00010000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.
        lda  #1
        sta  cx16.VERA_ADDR_M       ; start at (0, 1)

-       jsr  _copy_line_to_buffer
        ; now copy the linebuffer back to the line above this one
        dec  cx16.VERA_ADDR_M
        jsr  _copy_buffer_to_line
        ; next line to copy
        inc  cx16.VERA_ADDR_M
        inc  cx16.VERA_ADDR_M
	    dec  P8ZP_SCRATCH_B1
	    bne  -
	    plx
	    rts

_copy_line_to_buffer:
        ldy  #0
        stz  cx16.VERA_ADDR_L
	    ldx  P8ZP_SCRATCH_REG
-       lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        lda  cx16.VERA_DATA0
        sta  _scrollbuffer,y
        iny
        dex
        bpl  -
        rts

_copy_buffer_to_line
	    ldy  #0
        stz  cx16.VERA_ADDR_L
	    ldx  P8ZP_SCRATCH_REG
-       lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        lda  _scrollbuffer,y
        sta  cx16.VERA_DATA0
        iny
        dex
        bpl  -
        rts

_scrollbuffer  .fill  160, 0
	}}
}

asmsub  scroll_down  (ubyte dummy @ Pc) clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	;      Carry flag is a dummy on the cx16
	; TODO use the VERA dual data port
	%asm {{
	    phx
	    jsr  c64.SCREEN
	    txa
	    lsr  a
	    lsr  a
	    dea
	    sta  P8ZP_SCRATCH_REG       ; number of columns / 4 due to loop unroll
	    dey
	    sty  P8ZP_SCRATCH_B1        ; number of lines to scroll up
	    dey
        stz  cx16.VERA_CTRL
        sty  cx16.VERA_ADDR_M       ; start at line before bottom line
        lda  #%00010000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

-       jsr  scroll_up._copy_line_to_buffer
        ; now copy the linebuffer back to the line below this one
        inc  cx16.VERA_ADDR_M
        jsr  scroll_up._copy_buffer_to_line
        ; next line to copy
        dec  cx16.VERA_ADDR_M
        dec  cx16.VERA_ADDR_M
	    dec  P8ZP_SCRATCH_B1
	    bne  -
	    plx
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

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A)  {
	; ---- sets the character in the screen matrix at the given position
	%asm {{
		pha
		txa
		asl  a
        stz  cx16.VERA_CTRL
		stz  cx16.VERA_ADDR_H
		sta  cx16.VERA_ADDR_L
		sty  cx16.VERA_ADDR_M
		pla
        sta  cx16.VERA_DATA0
		rts
	}}
}

asmsub  getchr  (ubyte col @A, ubyte row @Y) -> ubyte @ A {
	; ---- get the character in the screen matrix at the given location
	%asm  {{
		asl  a
        stz  cx16.VERA_CTRL
		stz  cx16.VERA_ADDR_H
		sta  cx16.VERA_ADDR_L
		sty  cx16.VERA_ADDR_M
        lda  cx16.VERA_DATA0
		rts
	}}
}

asmsub  setclr  (ubyte col @X, ubyte row @Y, ubyte color @A) clobbers(A)  {
	; ---- set the color in A on the screen matrix at the given position
	%asm {{
		pha
		txa
		asl  a
		ina
        stz  cx16.VERA_CTRL
		stz  cx16.VERA_ADDR_H
		sta  cx16.VERA_ADDR_L
		sty  cx16.VERA_ADDR_M
		pla
        sta  cx16.VERA_DATA0
		rts
	}}
}

asmsub  getclr  (ubyte col @A, ubyte row @Y) -> ubyte @ A {
	; ---- get the color in the screen color matrix at the given location
	%asm  {{
		asl  a
		ina
        stz  cx16.VERA_CTRL
		stz  cx16.VERA_ADDR_H
		sta  cx16.VERA_ADDR_L
		sty  cx16.VERA_ADDR_M
        lda  cx16.VERA_DATA0
		rts
	}}
}

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
        stz  cx16.VERA_CTRL
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
