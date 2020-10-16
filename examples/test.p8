%import textio
%import conv
%zeropage basicsafe

main {
    %option force_output

    sub start() {

        str hex1 = "aap2"
        str hex2 = "aap1je"
        str hex3 = "aap1JE"
        str hex4 = "aap3333"

        byte result
        result = strcmp(hex1, hex1)
        txt.print_b(result)
        txt.chrout('\n')
        result = strcmp(hex1, hex2)
        txt.print_b(result)
        txt.chrout('\n')
        result = strcmp(hex1, hex3)
        txt.print_b(result)
        txt.chrout('\n')
        result = strcmp(hex1, hex4)
        txt.print_b(result)
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
