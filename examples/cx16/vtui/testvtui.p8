%import textio
%option no_sysinit
%zeropage basicsafe

; simple test program for the "VTUI" text user interface library
; see:  https://github.com/JimmyDansbo/VTUIlib

main {
    sub start() {
        vtui.initialize()
        store_logo()

        txt.lowercase()
        vtui.screen_set(0)
        vtui.clr_scr('%', $50)
        vtui.gotoxy(5,5)
        vtui.fill_box(':', 70, 50, $c6)
        vtui.gotoxy(10,10)
        vtui.border(1, 40, 6, $47)
        vtui.gotoxy(12,12)
        vtui.print_str2(sc:"Hello, world! vtui from Prog8!", $f2, false)
        vtui.gotoxy(12,13)
        vtui.print_str2("Hello, world! vtui from Prog8!", $f2, true)

        str inputbuffer = "?" * 20

;        txt.print_uwhex(inputbuffer, 1)
;        txt.chrout(':')
;        txt.print(inputbuffer)
;        txt.chrout('\n')

        vtui.gotoxy(5,20)
        vtui.print_str2(sc:"Enter your name: ", $e3, false)
        ubyte length = vtui.input_str(inputbuffer, len(inputbuffer), $21)

        vtui.gotoxy(8,22)
        vtui.print_str2(sc:"Your name is: ", $e3, false)
        ;vtui.print_str2(inputbuffer, $67, $00)
        vtui.print_str(inputbuffer, length, $67, $00)

        ; txt.uppercase()   ; kills vtui?
        logo_mover()
    }

    sub store_logo() {
        vtui.gotoxy(0, 0)
        vtui.save_rect($80, 1, $0000, 7, 7)
        vtui.gotoxy(0, 0)
        vtui.save_rect($80, 1, $0100, 7, 7)
    }

    sub logo_mover() {
        ubyte xcoord = 0
        ubyte ycoord = 0
        ubyte newx = 0
        ubyte newy = 0

        ;vtui.screen_set(2)
        vtui.gotoxy(30, 32)
        vtui.print_str2("arrow keys to move!", $61, true)

char_loop:
        ubyte char = c64.GETIN()
        if not char
            goto char_loop

        when char {
            $91 -> {
                if newy {
                    newy--
                    move_logo()
                }
            }
            $11 -> {
                if newy<53 {
                    newy++
                    move_logo()
                }
            }
            $9d -> {
                if newx {
                    newx--
                    move_logo()
                }
            }
            $1d -> {
                if newx<70 {
                    newx++
                    move_logo()
                }
            }
        }

        goto char_loop

        sub move_logo() {
            vtui.gotoxy(xcoord, ycoord)
            vtui.rest_rect($80, 1, $0100, 7, 7)
            vtui.gotoxy(newx, newy)
            vtui.save_rect($80, 1, $0100, 7, 7)
            vtui.gotoxy(newx, newy)
            vtui.rest_rect($80, 1, $0000, 7, 7)
            xcoord = newx
            ycoord = newy
        }
    }

}


vtui $1000 {

    %asmbinary "VTUI1.0.BIN", 2     ; skip the 2 dummy load address bytes

    ; NOTE: base address $1000 here must be the same as the block's memory address, for obvious reasons!
    ; The routines below are for VTUI 1.0
    romsub $1000  =  initialize() clobbers(A, X, Y)
    romsub $1002  =  screen_set(ubyte mode @A) clobbers(A, X, Y)
    romsub $1005  =  set_bank(ubyte bank @Pc) clobbers(A)
    romsub $1008  =  set_stride(ubyte stride @A) clobbers(A)
    romsub $100b  =  set_decr(ubyte incrdecr @Pc) clobbers(A)
    romsub $100e  =  clr_scr(ubyte char @A, ubyte colors @X) clobbers(Y)
    romsub $1011  =  gotoxy(ubyte column @A, ubyte row @Y)
    romsub $1014  =  plot_char(ubyte char @A, ubyte colors @X)
    romsub $1017  =  scan_char() -> ubyte @A, ubyte @X
    romsub $101a  =  hline(ubyte char @A, ubyte length @Y, ubyte colors @X) clobbers(A)
    romsub $101d  =  vline(ubyte char @A, ubyte height @Y, ubyte colors @X) clobbers(A)
    romsub $1020  =  print_str(str txtstring @R0, ubyte length @Y, ubyte colors @X, ubyte convertchars @A) clobbers(A, Y)
    romsub $1023  =  fill_box(ubyte char @A, ubyte width @R1, ubyte height @R2, ubyte colors @X) clobbers(A, Y)
    romsub $1026  =  pet2scr(ubyte char @A) -> ubyte @A
    romsub $1029  =  scr2pet(ubyte char @A) -> ubyte @A
    romsub $102c  =  border(ubyte mode @A, ubyte width @R1, ubyte height @R2, ubyte colors @X) clobbers(Y)       ; NOTE: mode 6 means 'custom' characters taken from r3 - r6
    romsub $102f  =  save_rect(ubyte ramtype @A, ubyte vbank @Pc, uword address @R0, ubyte width @R1, ubyte height @R2) clobbers(A, X, Y)
    romsub $1032  =  rest_rect(ubyte ramtype @A, ubyte vbank @Pc, uword address @R0, ubyte width @R1, ubyte height @R2) clobbers(A, X, Y)
    romsub $1035  =  input_str(uword buffer @R0, ubyte buflen @Y, ubyte colors @X) clobbers (A) -> ubyte @Y
    romsub $1038  =  get_bank() clobbers (A) -> ubyte @Pc
    romsub $103b  =  get_stride() -> ubyte @A
    romsub $103e  =  get_decr() clobbers (A) -> ubyte @Pc

    ; -- helper function to do string length counting for you internally, and turn the convertchars flag into a boolean again
    asmsub print_str2(str txtstring @R0, ubyte colors @X, ubyte convertchars @Pc) clobbers(A, Y) {
        %asm {{
            lda  #0
            bcs  +
            lda  #$80
+           pha
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  prog8_lib.strlen
            pla
            jmp  print_str
        }}
    }
}
