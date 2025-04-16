; Prog8 definitions for the Text I/O and Screen routines for the CommanderX16
; All routines work with Screencode character encoding, except `print`, `chrout` and `input_chars`,
; these work with PETSCII encoding instead.

%import syslib
%import conv
%import shared_cbm_textio_functions

txt {

    %option no_symbol_prefixing, ignore_unused

const ubyte DEFAULT_WIDTH = 80
const ubyte DEFAULT_HEIGHT = 60
const long VERA_TEXTMATRIX = $1b000

extsub $FFD2 = chrout(ubyte character @ A)    ; for consistency. You can also use cbm.CHROUT directly ofcourse. Note: takes a PETSCII encoded character.


sub clear_screen() {
    chrout(147)
}

sub cls() {
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

asmsub get_cursor() -> ubyte @X, ubyte @Y {
    %asm {{
        sec
        jsr  cbm.PLOT
        phx     ; swap X and Y
        phy
        plx
        ply
        rts
    }}
}

asmsub  fill_screen (ubyte character @ A, ubyte color @ Y) clobbers(A, X)  {
	; ---- fill the character screen with the given fill character and character color.
	%asm {{
_color = P8ZP_SCRATCH_B1
_numrows = P8ZP_SCRATCH_REG
        sty  _color
        pha
        jsr  cbm.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _numrows
        lda  #%00010000
        jsr  set_vera_textmatrix_addresses
        pla
_more   ldx  _numrows
        phy
        ldy  _color
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
        bra  _more
+       rts

set_vera_textmatrix_addresses:
        stz  cx16.VERA_CTRL
        ora  #VERA_TEXTMATRIX>>16
        sta  cx16.VERA_ADDR_H
        stz  cx16.VERA_ADDR_L       ; start at (0,0)
        lda  #>VERA_TEXTMATRIX
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
        sta  P8ZP_SCRATCH_REG
        lda  #%00100000
        jsr  fill_screen.set_vera_textmatrix_addresses
        pla
_more   ldx  P8ZP_SCRATCH_REG
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
        bra  _more
+       rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(X, Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
_color = P8ZP_SCRATCH_B1
_numrows = P8ZP_SCRATCH_REG
        sta  _color
        jsr  cbm.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _numrows
        stz  cx16.VERA_CTRL
        lda  #%00100000
        jsr  fill_screen.set_vera_textmatrix_addresses
        inc  cx16.VERA_ADDR_L       ; start at (1,0) - the color attribute byte
_more   ldx  _numrows
        lda  _color
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
        bra  _more
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
    ; -- switch to iso-8859-15 character set
    cbm.CHROUT($0f)
}

sub iso_off() {
    ; -- you have to call this first when switching back from iso charset to regular charset.
    cbm.CHROUT($8f)
}

sub cp437() {
    ; -- switch to CP-437 (ibm PC) character set
    cbm.CHROUT($0f)                 ; iso mode
    cx16.screen_set_charset(7, 0)   ; charset
    %asm {{
        clc
        ldx  #95        ; underscore
        lda  #cx16.EXTAPI_iso_cursor_char
        jsr  cx16.extapi
    }}
}

sub iso5() {
    ; -- switch to iso-8859-5 character set (Cyrillic)
    cbm.CHROUT($0f)                 ; iso mode
    cx16.screen_set_charset(8, 0)   ; charset
}

sub iso16() {
    ; -- switch to iso-8859-16 character set (Eastern Europe)
    cbm.CHROUT($0f)                 ; iso mode
    cx16.screen_set_charset(10, 0)  ; charset
}

sub kata() {
    ; -- switch to katakana character set (requires rom 48+)
    cbm.CHROUT($0f)                 ; iso mode
    cx16.screen_set_charset(12, 0)  ; charset
}

asmsub  scroll_left() clobbers(A, X, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
    ; TODO: Romable
	%asm {{
	    jsr  cbm.SCREEN
	    dex
	    stx  _lx+1
        dey
        sty  P8ZP_SCRATCH_B1    ; number of rows to scroll

_nextline
        stz  cx16.VERA_CTRL     ; data port 0: source column
        lda  #%00010000 | VERA_TEXTMATRIX>>16        ; auto increment 1
        sta  cx16.VERA_ADDR_H
        lda  #2
        sta  cx16.VERA_ADDR_L   ; begin in column 1
        lda  P8ZP_SCRATCH_B1
        clc
        adc  #>VERA_TEXTMATRIX
        tay
        sty  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL     ; data port 1: destination column
        lda  #%00010000  | VERA_TEXTMATRIX>>16         ; auto increment 1
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
    ; TODO: Romable
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
        lda  #%00011000 | VERA_TEXTMATRIX>>16        ; auto decrement 1
        sta  cx16.VERA_ADDR_H
_rcol   lda  #79*2-1            ; modified
        sta  cx16.VERA_ADDR_L   ; begin in rightmost column minus one
        lda  P8ZP_SCRATCH_B1
        clc
        adc  #>VERA_TEXTMATRIX
        tay
        sty  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL     ; data port 1: destination column
        lda  #%00011000 | VERA_TEXTMATRIX>>16        ; auto decrement 1
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
    ; TODO: Romable
	%asm {{
	    jsr  cbm.SCREEN
	    stx  _nextline+1
	    dey
        sty  P8ZP_SCRATCH_B1
        stz  cx16.VERA_CTRL         ; data port 0 is source
        lda  #1 | (>VERA_TEXTMATRIX)
        sta  cx16.VERA_ADDR_M       ; start at second line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX>>16
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1 is destination
        lda  #>VERA_TEXTMATRIX
        sta  cx16.VERA_ADDR_M       ; start at top line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX>>16
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
    ; TODO: Romable
	%asm {{
	    jsr  cbm.SCREEN
	    stx  _nextline+1
	    dey
        sty  P8ZP_SCRATCH_B1
        stz  cx16.VERA_CTRL         ; data port 0 is source
        dey
        tya
        clc
        adc  #>VERA_TEXTMATRIX
        sta  cx16.VERA_ADDR_M       ; start at line before bottom line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX>>16
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1 is destination
        iny
        tya
        clc
        adc  #>VERA_TEXTMATRIX
        sta  cx16.VERA_ADDR_M       ; start at bottom line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000 | VERA_TEXTMATRIX>>16
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

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A)  {
	; ---- sets the character in the screen matrix at the given position
	%asm {{
            pha
            stz  cx16.VERA_CTRL
            lda  #VERA_TEXTMATRIX>>16
            sta  cx16.VERA_ADDR_H
            txa
            asl  a
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX
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
            lda  #VERA_TEXTMATRIX>>16
            sta  cx16.VERA_ADDR_H
            pla
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX
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
            lda  #VERA_TEXTMATRIX>>16
            sta  cx16.VERA_ADDR_H
            txa
            asl  a
            ina
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX
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
            lda  #VERA_TEXTMATRIX>>16
            sta  cx16.VERA_ADDR_H
            pla
            sta  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX
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
            lda  #VERA_TEXTMATRIX>>16
            sta  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            tya
            ;clc
            adc  #>VERA_TEXTMATRIX
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
            lda  #VERA_TEXTMATRIX>>16
            sta  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            tya
            ; clc
            adc  #>VERA_TEXTMATRIX
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

asmsub chrout_lit(ubyte character @A) {
    ; -- print the character always as a literal character, not as possible control code.
    %asm {{
        tax
        lda  #128
        jsr  cbm.CHROUT
        txa
        jmp  cbm.CHROUT
    }}
}

asmsub print_lit(str text @ AY) clobbers(A,Y)  {
    ; -- print zero terminated string, from A/Y, as all literal characters (no control codes)
    %asm {{
        sta  P8ZP_SCRATCH_W2
        sty  P8ZP_SCRATCH_W2+1
        ldy  #0
-       lda  (P8ZP_SCRATCH_W2),y
        beq  +
        tax
        lda  #128
        jsr  cbm.CHROUT
        txa
        jsr  cbm.CHROUT
        iny
        bne  -
+       rts
    }}
}

sub t256c(bool enable) {
    ; set 256 color tile mode on or off
    if enable
        cx16.VERA_L1_CONFIG |= 8
    else
        cx16.VERA_L1_CONFIG &= ~8
}

}
