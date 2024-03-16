; Prog8 definitions for the Text I/O and Screen routines for the CommanderX16
; All routines work with Screencode character encoding, except `print`, `chrout` and `input_chars`,
; these work with PETSCII encoding instead.

%import syslib
%import conv
%import shared_textio_functions

txt {

    %option no_symbol_prefixing, ignore_unused

const ubyte DEFAULT_WIDTH = 80
const ubyte DEFAULT_HEIGHT = 60

const ubyte VERA_TEXTMATRIX_BANK = 1
const uword VERA_TEXTMATRIX_ADDR = $b000


sub clear_screen() {
    chrout(147)
}

sub home() {
    chrout(19)
}

sub nl() {
    chrout('\n')
}

sub spc() {
    chrout(' ')
}

sub bell() {
    chrout(7)
}

asmsub column(ubyte col @A) clobbers(A, X, Y) {
    ; ---- set the cursor on the given column (starting with 0) on the current line
    %asm {{
        pha
        sec
        jsr  cbm.PLOT
        pla
        tay
        clc
        jmp  cbm.PLOT
    }}
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
        pha
        sec
        jsr  cbm.PLOT
        pla
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

asmsub  fill_screen (ubyte character @ A, ubyte color @ Y) clobbers(A, X)  {
	; ---- fill the character screen with the given fill character and character color.
	%asm {{
        sty  _ly+1
        pha
        jsr  cbm.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        lda  #%00010000
        jsr  set_vera_textmatrix_addresses
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
+       rts

set_vera_textmatrix_addresses:
        stz  cx16.VERA_CTRL
        ora  #VERA_TEXTMATRIX_BANK
        sta  cx16.VERA_ADDR_H
        stz  cx16.VERA_ADDR_L       ; start at (0,0)
        lda  #>VERA_TEXTMATRIX_ADDR
        sta  cx16.VERA_ADDR_M
        rts

        }}
}

asmsub  clear_screenchars (ubyte character @ A) clobbers(X, Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
        pha
        jsr  cbm.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        lda  #%00100000
        jsr  fill_screen.set_vera_textmatrix_addresses
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
+       rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(X, Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
        sta  _la+1
        jsr  cbm.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00100000
        jsr  fill_screen.set_vera_textmatrix_addresses
        inc  cx16.VERA_ADDR_L       ; start at (1,0) - the color attribute byte
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
+       rts
        }}
}


ubyte[16] color_to_charcode = [$90,$05,$1c,$9f,$9c,$1e,$1f,$9e,$81,$95,$96,$97,$98,$99,$9a,$9b]

sub color (ubyte txtcol) {
    txtcol &= 15
    cbm.CHROUT(color_to_charcode[txtcol])
}

sub color2 (ubyte txtcol, ubyte bgcol) {
    txtcol &= 15
    bgcol &= 15
    cbm.CHROUT(color_to_charcode[bgcol])
    cbm.CHROUT(1)       ; switch fg and bg colors
    cbm.CHROUT(color_to_charcode[txtcol])
}

sub lowercase() {
    cbm.CHROUT($0e)
    ; this is not 100% compatible: cx16.screen_set_charset(3, 0)  ; lowercase petscii charset
}

sub uppercase() {
    cbm.CHROUT($8e)
    ; this is not 100% compatible: cx16.screen_set_charset(2, 0)  ; uppercase petscii charset
}

sub iso() {
    cbm.CHROUT($0f)
    ; This doesn't enable it completely: cx16.screen_set_charset(1, 0)  ; iso charset
}

sub iso_off() {
    ; -- you have to call this first when switching back from iso charset to regular charset.
    cbm.CHROUT($8f)
}


asmsub  scroll_left() clobbers(A, X, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	%asm {{
	    jsr  cbm.SCREEN
	    dex
	    stx  _lx+1
        dey
        sty  P8ZP_SCRATCH_B1    ; number of rows to scroll

_nextline
        stz  cx16.VERA_CTRL     ; data port 0: source column
        lda  #%00010000 | VERA_TEXTMATRIX_BANK        ; auto increment 1
        sta  cx16.VERA_ADDR_H
        lda  #2
        sta  cx16.VERA_ADDR_L   ; begin in column 1
        lda  P8ZP_SCRATCH_B1
        clc
        adc  #>VERA_TEXTMATRIX_ADDR
        tay
        sty  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL     ; data port 1: destination column
        lda  #%00010000  | VERA_TEXTMATRIX_BANK         ; auto increment 1
        sta  cx16.VERA_ADDR_H
        stz  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M

_lx     ldx  #0                ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        bpl  _nextline

        lda  #0
        sta  cx16.VERA_CTRL
	    rts
	}}
}

asmsub  scroll_right() clobbers(A,X,Y)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	%asm {{
	    jsr  cbm.SCREEN
	    dex
	    stx  _lx+1
	    txa
	    asl  a
	    dea
	    sta  _rcol+1
	    ina
	    ina
	    sta  _rcol2+1
        dey
        sty  P8ZP_SCRATCH_B1    ; number of rows to scroll

_nextline
        stz  cx16.VERA_CTRL     ; data port 0: source column
        lda  #%00011000 | VERA_TEXTMATRIX_BANK        ; auto decrement 1
        sta  cx16.VERA_ADDR_H
_rcol   lda  #79*2-1            ; modified
        sta  cx16.VERA_ADDR_L   ; begin in rightmost column minus one
        lda  P8ZP_SCRATCH_B1
        clc
        adc  #>VERA_TEXTMATRIX_ADDR
        tay
        sty  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL     ; data port 1: destination column
        lda  #%00011000 | VERA_TEXTMATRIX_BANK        ; auto decrement 1
        sta  cx16.VERA_ADDR_H
_rcol2  lda  #79*2+1           ; modified
        sta  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M

_lx     ldx  #0                 ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        bpl  _nextline

        lda  #0
        sta  cx16.VERA_CTRL
	    rts
	}}
}

asmsub  scroll_up() clobbers(A, X, Y)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	%asm {{
	    jsr  cbm.SCREEN
	    stx  _nextline+1
	    dey
        sty  P8ZP_SCRATCH_B1
        stz  cx16.VERA_CTRL         ; data port 0 is source
        lda  #1 | (>VERA_TEXTMATRIX_ADDR)
        sta  cx16.VERA_ADDR_M       ; start at second line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX_BANK
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1 is destination
        lda  #>VERA_TEXTMATRIX_ADDR
        sta  cx16.VERA_ADDR_M       ; start at top line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX_BANK
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

_nextline
        ldx  #80        ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1        ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1        ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        beq  +
        stz  cx16.VERA_CTRL         ; data port 0
        stz  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1
        stz  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M
        bra  _nextline

+       lda  #0
        sta  cx16.VERA_CTRL
	    rts
	}}
}

asmsub  scroll_down() clobbers(A, X, Y)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	%asm {{
	    jsr  cbm.SCREEN
	    stx  _nextline+1
	    dey
        sty  P8ZP_SCRATCH_B1
        stz  cx16.VERA_CTRL         ; data port 0 is source
        dey
        tya
        clc
        adc  #>VERA_TEXTMATRIX_ADDR
        sta  cx16.VERA_ADDR_M       ; start at line before bottom line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX_BANK
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1 is destination
        iny
        tya
        clc
        adc  #>VERA_TEXTMATRIX_ADDR
        sta  cx16.VERA_ADDR_M       ; start at bottom line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX_BANK
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

_nextline
        ldx  #80        ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1        ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1        ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        beq  +
        stz  cx16.VERA_CTRL         ; data port 0
        stz  cx16.VERA_ADDR_L
        dec  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1
        stz  cx16.VERA_ADDR_L
        dec  cx16.VERA_ADDR_M
        bra  _nextline

+       lda  #0
        sta  cx16.VERA_CTRL
	    rts
	}}
}

romsub $FFD2 = chrout(ubyte character @ A)    ; for consistency. You can also use cbm.CHROUT directly ofcourse. Note: takes a PETSCII encoded character.

asmsub  print (str text @ AY) clobbers(A,Y)  {
	; ---- print null terminated string, in PETSCII encoding, from A/Y
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
		bra  _ones
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
		bra  print_ub._print_byte_digits
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
		bra  print_ubbin
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
		bra  print_ubhex
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
		ina
		bne +
		iny
+		bra  print_uw
	}}
}

asmsub  input_chars  (uword buffer @ AY) clobbers(A) -> ubyte @ Y  {
	; ---- Input a string (max. 80 chars) from the keyboard, in PETSCII encoding.
	;      Returns length in Y. (string is terminated with a 0 byte as well)
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

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A)  {
	; ---- sets the character in the screen matrix at the given position
	%asm {{
            pha
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX_BANK
            sta  cx16.VERA_ADDR_H
            txa
            asl  a
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX_ADDR
            sta  cx16.VERA_ADDR_M
            pla
            sta  cx16.VERA_DATA0
            rts
	}}
}

asmsub  getchr  (ubyte col @A, ubyte row @Y) -> ubyte @ A {
	; ---- get the character in the screen matrix at the given location
	%asm  {{
            asl  a
            pha
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX_BANK
            sta  cx16.VERA_ADDR_H
            pla
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX_ADDR
            sta  cx16.VERA_ADDR_M
            lda  cx16.VERA_DATA0
            rts
	}}
}

asmsub  setclr  (ubyte col @X, ubyte row @Y, ubyte color @A) clobbers(A)  {
	; ---- set the color in A on the screen matrix at the given position
	;      note: on the CommanderX16 this allows you to set both Fg and Bg colors;
	;            use the high nybble in A to set the Bg color!
	%asm {{
            pha
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX_BANK
            sta  cx16.VERA_ADDR_H
            txa
            asl  a
            ina
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX_ADDR
            sta  cx16.VERA_ADDR_M
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
            pha
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX_BANK
            sta  cx16.VERA_ADDR_H
            pla
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX_ADDR
            sta  cx16.VERA_ADDR_M
            lda  cx16.VERA_DATA0
            rts
	}}
}

sub  setcc  (ubyte col, ubyte row, ubyte character, ubyte charcolor) {
	; ---- set char+color at the given position on the screen
	;      note: color handling is the same as on the C64: it only sets the foreground color and leaves the background color as is.
	;            Use setcc2 if you want Cx-16 specific feature of setting both Bg+Fg colors (is faster as well).
	%asm {{
            lda  col
            asl  a
            tax
            ldy  row
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX_BANK
            sta  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            tya
            ;clc
            adc  #>VERA_TEXTMATRIX_ADDR
            sta  cx16.VERA_ADDR_M
            lda  character
            sta  cx16.VERA_DATA0
            inc  cx16.VERA_ADDR_L
            lda  charcolor
            and  #$0f
            sta  P8ZP_SCRATCH_B1
            lda  cx16.VERA_DATA0
            and  #$f0
            ora  P8ZP_SCRATCH_B1
            sta  cx16.VERA_DATA0
            rts
    }}
}

sub  setcc2  (ubyte col, ubyte row, ubyte character, ubyte colors)  {
	; ---- set char+color at the given position on the screen
	;      note: on the CommanderX16 this allows you to set both Fg and Bg colors;
	;            use the high nybble in A to set the Bg color! Is a bit faster than setcc() too.
	%asm {{
            lda  col
            asl  a
            tax
            ldy  row
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX_BANK
            sta  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX_ADDR
            sta  cx16.VERA_ADDR_M
            lda  character
            sta  cx16.VERA_DATA0
            inc  cx16.VERA_ADDR_L
            lda  colors
            sta  cx16.VERA_DATA0
            rts
    }}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ X) {
	%asm  {{
		clc
		jmp  cbm.PLOT
	}}
}

asmsub width() clobbers(X,Y) -> ubyte @A {
    ; -- returns the text screen width (number of columns)
    %asm {{
        jsr  cbm.SCREEN
        txa
        rts
    }}
}

asmsub height() clobbers(X, Y) -> ubyte @A {
    ; -- returns the text screen height (number of rows)
    %asm {{
        jsr  cbm.SCREEN
        tya
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
