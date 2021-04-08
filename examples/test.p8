%target cx16
%import textio
%option no_sysinit
%zeropage basicsafe

; simple test program for the "VTUI" text user interface library
; see:  https://github.com/JimmyDansbo/VTUIlib

main {
    sub start() {
        vtui.initialize()
        txt.print("ok")
    }
}


vtui $1000 {

    %asmbinary "cx16/vtui/VTUI0.8.BIN", 2     ; skip the 2 dummy load address bytes

    ; NOTE: base address $1000 here must be the same as the block's memory address, for obvious reasons!
    ; The routines below are for VTUI 0.8
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

    ; -- helper function to do string length counting for you internally
    asmsub print_str2(str txtstring @R0, ubyte colors @X, ubyte convertchars @A) clobbers(A, Y) {
        %asm {{
            pha
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  prog8_lib.strlen
            pla
            jmp  print_str
        }}
    }
}
