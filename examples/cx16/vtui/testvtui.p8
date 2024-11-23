%import textio
%option no_sysinit
%zeropage basicsafe

; simple test program for the "VTUI" text user interface library
; see:  https://github.com/JimmyDansbo/VTUIlib

main {
    sub start() {
        vtui.initialize()
        store_logo()            ; capture logo before boxes are drawn

        txt.lowercase()
        vtui.screen_set(0)
        vtui.clr_scr('%', $50)
        vtui.gotoxy(5,5)
        vtui.fill_box(':', 70, 50, $c6)

        store_where_logo_was()  ; after vtui draws boxes, initialize replacement screen values as logo moves

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
        vtui.save_rect($80, true, $0000, 7, 7)
    }

    sub store_where_logo_was() {
        vtui.gotoxy(0, 0)
        vtui.save_rect($80, true, $0100, 7, 7)
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
        ubyte char
        void, char = cbm.GETIN()
        if char==0
            goto char_loop

        when char {
            $91 -> {
                if newy!=0 {
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
                if newx!=0 {
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
            vtui.rest_rect($80, true, $0100, 7, 7)
            vtui.gotoxy(newx, newy)
            vtui.save_rect($80, true, $0100, 7, 7)
            vtui.gotoxy(newx, newy)
            vtui.rest_rect($80, true, $0000, 7, 7)
            xcoord = newx
            ycoord = newy
        }
    }

}


vtui $1000 {

    %option no_symbol_prefixing
    %asmbinary "VTUI1.2.BIN", 2     ; skip the 2 dummy load address bytes

    ; NOTE: base address $1000 here must be the same as the block's memory address, for obvious reasons!
    ; The routines below are for VTUI 1.0
    const uword vtjmp = $1002
    extsub vtjmp - 2   =  initialize() clobbers(A, X, Y)
    extsub vtjmp + 0*3 =  screen_set(ubyte mode @A) clobbers(A, X, Y)
    extsub vtjmp + 1*3  =  set_bank(bool bank1 @Pc) clobbers(A)
    extsub vtjmp + 2*3  =  set_stride(ubyte stride @A) clobbers(A)
    extsub vtjmp + 3*3  =  set_decr(bool incrdecr @Pc) clobbers(A)
    extsub vtjmp + 4*3  =  clr_scr(ubyte char @A, ubyte colors @X) clobbers(Y)
    extsub vtjmp + 5*3  =  gotoxy(ubyte column @A, ubyte row @Y)
    extsub vtjmp + 6*3  =  plot_char(ubyte char @A, ubyte colors @X)
    extsub vtjmp + 7*3  =  scan_char() -> ubyte @A, ubyte @X
    extsub vtjmp + 8*3  =  hline(ubyte char @A, ubyte length @Y, ubyte colors @X) clobbers(A)
    extsub vtjmp + 9*3  =  vline(ubyte char @A, ubyte height @Y, ubyte colors @X) clobbers(A)
    extsub vtjmp + 10*3 =  print_str(str txtstring @R0, ubyte length @Y, ubyte colors @X, ubyte convertchars @A) clobbers(A, Y)
    extsub vtjmp + 11*3 =  fill_box(ubyte char @A, ubyte width @R1, ubyte height @R2, ubyte colors @X) clobbers(A, Y)
    extsub vtjmp + 12*3 =  pet2scr(ubyte char @A) -> ubyte @A
    extsub vtjmp + 13*3 =  scr2pet(ubyte char @A) -> ubyte @A
    extsub vtjmp + 14*3 =  border(ubyte mode @A, ubyte width @R1, ubyte height @R2, ubyte colors @X) clobbers(Y)       ; NOTE: mode 6 means 'custom' characters taken from r3 - r6
    extsub vtjmp + 15*3 =  save_rect(ubyte ramtype @A, bool vbank1 @Pc, uword address @R0, ubyte width @R1, ubyte height @R2) clobbers(A, X, Y)
    extsub vtjmp + 16*3 =  rest_rect(ubyte ramtype @A, bool vbank1 @Pc, uword address @R0, ubyte width @R1, ubyte height @R2) clobbers(A, X, Y)
    extsub vtjmp + 17*3 =  input_str(uword buffer @R0, ubyte buflen @Y, ubyte colors @X) clobbers (A) -> ubyte @Y     ; Y=length of input
    extsub vtjmp + 18*3 =  get_bank() clobbers (A) -> bool @Pc
    extsub vtjmp + 19*3 =  get_stride() -> ubyte @A
    extsub vtjmp + 20*3 =  get_decr() clobbers (A) -> bool @Pc

    ; -- helper function to do string length counting for you internally, and turn the convertchars flag into a boolean again
    asmsub print_str2(str txtstring @R0, ubyte colors @X, bool convertchars @Pc) clobbers(A, Y) {
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
