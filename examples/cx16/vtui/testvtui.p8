%target cx16
%import textio
%zeropage dontuse

main {
    sub start() {
        txt.lowercase()
        vtui.initialize()
        vtui.screen_set(2)
        vtui.gotoxy(0,0)
        vtui.fill_box(':', 80, 60, $c6)
        vtui.gotoxy(10,10)
        vtui.border(1, 40, 6, $47)
        vtui.gotoxy(12,12)
        vtui.print_str(@"Hello, world! VTUI from Prog8!", $f2, false)

        repeat {
        }
    }
}


vtui $1000 {

    %asmbinary "VTUI0.4.BIN", 2     ; skip the 2 dummy load address bytes

    ; NOTE: base address $1000 here must be the same as the block's memory address, for obvious reasons!
    romsub $1000  =  initialize() clobbers(A, X, Y)
    romsub $1002  =  screen_set(ubyte mode @A) clobbers(A, X, Y)
    romsub $1005  =  set_bank(ubyte bank @Pc) clobbers(A)
    romsub $1008  =  set_stride(ubyte stride @A) clobbers(A)
    romsub $100b  =  set_decr(ubyte incrdecr @Pc) clobbers(A)
    romsub $100e  =  gotoxy(ubyte column @A, ubyte row @Y)
    romsub $1011  =  plot_char(ubyte char @A, ubyte colors @X)
    romsub $1014  =  scan_char() -> ubyte @A, ubyte @X
    romsub $1017  =  hline(ubyte char @A, ubyte length @Y, ubyte colors @X) clobbers(A)
    romsub $101a  =  vline(ubyte char @A, ubyte length @Y, ubyte colors @X) clobbers(A)
    romsub $101d  =  print_str(str string @R0, ubyte colors @X, ubyte convertchars @Pc) clobbers(A, Y)
    romsub $1020  =  fill_box(ubyte char @A, ubyte width @R1, ubyte height @R2, ubyte colors @X) clobbers(A, Y)
    romsub $1023  =  pet2scr(ubyte char @A) -> ubyte @A
    romsub $1026  =  scr2pet(ubyte char @A) -> ubyte @A
    romsub $1029  =  border(ubyte mode @A, ubyte width @R1, ubyte height @R2, ubyte colors @X) clobbers(Y)       ; NOTE: mode 6 means 'custom' characters taken from r3 - r6
    romsub $102c  =  save_rect(ubyte vbank @A, uword address @R0, ubyte width @R1, ubyte height @R2, ubyte ramtype @Pc) clobbers(A, X, Y)
    romsub $102f  =  rest_rect(ubyte vbank @A, uword address @R0, ubyte width @R1, ubyte height @R2, ubyte ramtype @Pc) clobbers(A, X, Y)
}
