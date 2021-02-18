%target cx16
%import textio
%zeropage dontuse

main {
    sub start() {
        txt.lowercase()
        vtui.initialize()
        vtui.screen_set(2)
        vtui.gotoxy(0,0)
        vtui.fill_box(mkword(':',0), mkword(60, 80), $c6)
        vtui.gotoxy(10,10)
        vtui.border(1, mkword(10, 40), $47)
        vtui.gotoxy(12,12)
        vtui.print_str("Hello, world! VTUI from Prog8!", $f2)

        vtui.set_decr(1)
        vtui.gotoxy(41,14)
        vtui.print_str("Hello, world! VTUI from Prog8!", $f2)

        repeat {
        }
    }
}

; TODO VTUI ideas:
; - question/bit of a problem: any particular reason fill_box() require the character to be in R0H (instead of R0L)?  This complicates things for me because Prog8 treats the R-registers as words. Can't it be in A, like the mode for border()?
; - question/bit of a problem: any particular reason fill_box(), border(), save_rect() and rest_rect() require the dimensions to be in a single R1 register rather than splitting it across say R1L and R2L? (same reason as above)
; - suggestion: add a few more box types that look nice with the Upper+Lowercase characters charset. Currently only border mode 0 and 1 are ok in this case. Perhaps let the user specify the box character?
; - suggestion: let the initialize function set the 'sane' / most used default settings for vera stride and increment, without requiring screen_set() ?
; - suggestion: the petscii-to-screencode translation and vice versa, is now quite restrictive. My "Hello, world! VTUI from Prog8!" is printed as "Vello world! VVVV from Vrog8!"  Can this perhaps be improved to map more characters
; - bug: calling initialize twice makes other calls crash
; - bug in doc: rest_rest() documentation is incorrect in the register table, 'Purpose' column seems copied from save_rect()
; - bug: a set_decr() with C=1 (decrement) seems to misalign character+color output for instance when using print_str() afterwards in an attempt to print in reverse.


vtui $4000 {

    %asmbinary "VTUI0.3.BIN", 2     ; skip the 2 dummy load address bytes

    ; NOTE: base address $4000 here must be the same as the block's memory address, for obvious reasons!
    romsub $4000  =  initialize() clobbers(A, X, Y)
    romsub $4002  =  screen_set(ubyte mode @A) clobbers(A, X, Y)
    romsub $4005  =  set_bank(ubyte bank @A) clobbers(A)
    romsub $4008  =  set_stride(ubyte stride @A) clobbers(A)
    romsub $400b  =  set_decr(ubyte incrdecr @Pc) clobbers(A)
    romsub $400e  =  gotoxy(ubyte column @A, ubyte row @Y)
    romsub $4011  =  plot_char(ubyte char @A, ubyte colors @X)
    romsub $4014  =  scan_char() -> ubyte @A, ubyte @X
    romsub $4017  =  hline(ubyte char @A, ubyte length @Y, ubyte colors @X) clobbers(A)
    romsub $401a  =  vline(ubyte char @A, ubyte length @Y, ubyte colors @X) clobbers(A)
    romsub $401d  =  print_str(str string @R0, ubyte colors @X) clobbers(A, Y)
    romsub $4020  =  fill_box(uword char @R0, uword dimensions @R1, ubyte colors @X) clobbers(A, Y)         ; TODO :  char must be in R0h....  R1L/R1H = width/height...
    romsub $4023  =  pet2scr(ubyte char @A) -> ubyte @A
    romsub $4026  =  scr2pet(ubyte char @A) -> ubyte @A
    romsub $4029  =  border(ubyte mode @A, uword dimensions @R1, ubyte colors @X) clobbers(Y)       ; TODO :  R1L/R1H = width/height...
    romsub $402c  =  save_rect(ubyte vbank @A, uword address @R0, uword dimensions @R1, ubyte ramtype @Pc) clobbers(A, X, Y)   ; TODO :  R1L/R1H = width/height...  move ramtype to first param once possible
    romsub $402f  =  rest_rect(ubyte vbank @A, uword address @R0, uword dimensions @R1, ubyte ramtype @Pc) clobbers(A, X, Y)   ; TODO :  R1L/R1H = width/height...  move ramtype to first param once possible
}
