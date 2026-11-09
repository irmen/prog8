; Text I/O routines for a VERA text layer on the Vera64X board for the C64.
; It configures VERA layer 1 as an 80x60 visible text mode, using the C64 character ROM
; as the tile set, and the same 128x64 text matrix layout as the Commander X16 text mode.
;
; The caller is responsible for resetting VERA first (vera.reset()); init() does not do that.
; All routines work with Screencode character encoding, except `print` and `chrout`,
; these work with PETSCII encoding instead.
; `print` and `chrout` only handle the PETSCII control codes 13 (return), 19 (home) and
; 147 (clear screen); all other control codes are ignored.

%import vera
%import vera_gfx
%import vera_palette

%option ignore_unused

vera_text {

    %option no_symbol_prefixing, ignore_unused

    const ubyte DEFAULT_WIDTH = 80
    const ubyte DEFAULT_HEIGHT = 60
    const long VERA_TEXTMATRIX = $1b000         ; vram address of the text matrix (bank 1, $b000)

    private const uword TEXTMATRIX_ADDR = $b000 ; the text matrix address within vram bank 1
    private const ubyte MAP_WIDTH = 128         ; the text matrix width in tiles

    private ubyte cursor_col
    private ubyte cursor_row
    private ubyte textcolor = $01               ; white on black, low nybble is foreground

    sub init() {
        ; set up the text layer just like the X16 text layer
        vera.VERA_CTRL = 0
        vera.VERA_DC_VIDEO = (vera.VERA_DC_VIDEO & %11001100) | %00100001     ; layer 1 only, force VGA output
        vera.VERA_DC_HSCALE = 128
        vera.VERA_DC_VSCALE = 128
        vera.VERA_L1_CONFIG = %01100000         ; tile mode, 8x8 tiles, 128x64 map
        vera.VERA_L1_MAPBASE = $d8              ; map at vram bank 1, $b000
        vera.VERA_L1_TILEBASE = $f8             ; tile data (charset) at vram bank 1, $f000
        vera.VERA_L1_HSCROLL = 0
        vera.VERA_L1_VSCROLL = 0

        vera_gfx.copy_petscii_charset()
        vera_palette.set_default16()

        clear_screen()
    }

    sub clear_screen() {
        ; fill the text matrix (128x60 cells) with blank characters and black attributes
        verafx.clear(1, TEXTMATRIX_ADDR, 0, MAP_WIDTH * DEFAULT_HEIGHT * 2 / 4)
        home()
    }

    sub home() {
        cursor_col = 0
        cursor_row = 0
    }

    sub plot(ubyte col, ubyte row) {
        ; -- set the software text cursor position
        cursor_col = col
        cursor_row = row
    }

    sub color(ubyte txtcol) {
        ; -- set the foreground color used by print() and chrout(), leaving the background as is
        textcolor = (textcolor & $f0) | (txtcol & $0f)
    }

    sub color2(ubyte txtcol, ubyte bgcol) {
        ; -- set both the foreground and background color used by print() and chrout()
        textcolor = ((bgcol & $0f) << 4) | (txtcol & $0f)
    }

    sub chrout(ubyte character) {
        ; -- output a single PETSCII character at the software cursor position
        if character == 13 {
            newline()
        } else if character == 19 {
            home()
        } else if character == 147 {
            clear_screen()
        } else if character >= 32 {
            setcc2(cursor_col, cursor_row, petscii2screencode(character), textcolor)
            advance()
        }
    }

    sub print(str text) {
        ; -- print a zero terminated PETSCII string at the software cursor position
        while @(text) != 0 {
            chrout(@(text))
            text++
        }
    }

    private sub advance() {
        cursor_col++
        if cursor_col >= DEFAULT_WIDTH {
            newline()
        }
    }

    private sub newline() {
        cursor_col = 0
        cursor_row++
        if cursor_row >= DEFAULT_HEIGHT {
            scroll_up()
            cursor_row = DEFAULT_HEIGHT - 1
        }
    }

    private sub scroll_up() {
        ; shift the text matrix one row up, then blank the last full map row
        verafx.copy(1, TEXTMATRIX_ADDR + 256, 1, TEXTMATRIX_ADDR, (DEFAULT_HEIGHT - 1) as uword * 64)
        ubyte col
        for col in 0 to MAP_WIDTH - 1 {
            setcc2(col, DEFAULT_HEIGHT - 1, 32, textcolor)
        }
    }

    asmsub setchr(ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A) {
        ; -- sets the character in the text matrix at the given position
        %asm {{
            pha
            lda  #0
            sta  vera.VERA_CTRL
            lda  #VERA_TEXTMATRIX>>16
            sta  vera.VERA_ADDR_H
            txa
            asl  a
            sta  vera.VERA_ADDR_L
            tya
            adc  #>VERA_TEXTMATRIX
            sta  vera.VERA_ADDR_M
            pla
            sta  vera.VERA_DATA0
            rts
        }}
    }

    asmsub setclr(ubyte col @X, ubyte row @Y, ubyte colors @A) clobbers(A) {
        ; -- set the color attribute byte in the text matrix at the given position
        ;    note: the high nybble is the background color, the low nybble the foreground color
        %asm {{
            pha
            lda  #0
            sta  vera.VERA_CTRL
            lda  #VERA_TEXTMATRIX>>16
            sta  vera.VERA_ADDR_H
            txa
            asl  a
            sta  vera.VERA_ADDR_L
            tya
            adc  #>VERA_TEXTMATRIX
            sta  vera.VERA_ADDR_M
            inc  vera.VERA_ADDR_L
            pla
            sta  vera.VERA_DATA0
            rts
        }}
    }

    sub setcc(ubyte col, ubyte row, ubyte character, ubyte charcolor) {
        ; -- set char and color at the given position
        ;    note: only the foreground color is set, the background color is left as is.
        ;    Use setcc2 if you want to set both background and foreground colors.
        %asm {{
            lda  col
            asl  a
            tax
            ldy  row
            lda  #0
            sta  vera.VERA_CTRL
            lda  #VERA_TEXTMATRIX>>16
            sta  vera.VERA_ADDR_H
            stx  vera.VERA_ADDR_L
            tya
            adc  #>VERA_TEXTMATRIX
            sta  vera.VERA_ADDR_M
            lda  character
            sta  vera.VERA_DATA0
            inc  vera.VERA_ADDR_L
            lda  charcolor
            and  #$0f
            sta  P8ZP_SCRATCH_B1
            lda  vera.VERA_DATA0
            and  #$f0
            ora  P8ZP_SCRATCH_B1
            sta  vera.VERA_DATA0
            rts
        }}
    }

    sub setcc2(ubyte col, ubyte row, ubyte character, ubyte colors) {
        ; -- set char and color attribute at the given position
        ;    note: the high nybble in colors is the background color, the low nybble the foreground color
        %asm {{
            lda  col
            asl  a
            tax
            ldy  row
            lda  #0
            sta  vera.VERA_CTRL
            lda  #VERA_TEXTMATRIX>>16
            sta  vera.VERA_ADDR_H
            stx  vera.VERA_ADDR_L
            tya
            adc  #>VERA_TEXTMATRIX
            sta  vera.VERA_ADDR_M
            lda  character
            sta  vera.VERA_DATA0
            inc  vera.VERA_ADDR_L
            lda  colors
            sta  vera.VERA_DATA0
            rts
        }}
    }

    private asmsub petscii2screencode(ubyte petscii_char @A) -> ubyte @A {
        ; -- convert a PETSCII character to a screencode, for use with the C64 character ROM
        %asm {{
            sta  P8ZP_SCRATCH_REG
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            tax
            lda  _petscii2scr_offsets,x
            eor  P8ZP_SCRATCH_REG
            rts
_petscii2scr_offsets
            .byte  128, 0, 64, 32, 64, 192, 128, 128
            ; !notreached!
        }}
    }
}
