%import textio
%import conv
%zeropage basicsafe

main {

    sub start() {

        str hex1 = "a4E"
        str hex2 = "$a4E"
        str hex3 = @"a4E"
        str hex4 = @"$a4E"
        str bin1 = "111111010010"
        str bin2 = "%111111010010"

        txt.print(hex1)
        txt.chrout('=')
        txt.print_uwhex(conv.hex2uword(hex1), true)
        txt.chrout('\n')
        txt.print(hex2)
        txt.chrout('=')
        txt.print_uwhex(conv.hex2uword(hex2), true)
        txt.chrout('\n')
        txt.print(hex3)
        txt.chrout('=')
        txt.print_uwhex(conv.hex2uword(hex3), true)
        txt.chrout('\n')
        txt.print(hex4)
        txt.chrout('=')
        txt.print_uwhex(conv.hex2uword(hex4), true)
        txt.chrout('\n')
        txt.print(bin1)
        txt.chrout('=')
        txt.print_uwbin(conv.bin2uword(bin1), true)
        txt.chrout('\n')
        txt.print(bin2)
        txt.chrout('=')
        txt.print_uwbin(conv.bin2uword(bin2), true)
        txt.chrout('\n')


        testX()
    }

    asmsub testX() {
        %asm {{
            stx  _saveX
            lda  #13
            jsr  txt.chrout
            lda  _saveX
            jsr  txt.print_ub
            lda  #13
            jsr  txt.chrout
            ldx  _saveX
            rts
_saveX   .byte 0
        }}
    }
}
