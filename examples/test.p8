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

    sub strcmp(uword s1, uword s2) -> byte {
        byte result = 0

        %asm {{
_loop       ldy  #0
            lda  (s1),y
            bne  +
            lda  (s2),y
            bne  _return_minusone
            beq  _return
+           lda  (s2),y
            sec
            sbc  (s1),y
            bmi  _return_one
            bne  _return_minusone
            inc  s1
            bne  +
            inc  s1+1
+           inc  s2
            bne  _loop
            inc  s2+1
            bne  _loop

_return_one
            ldy  #1
            sty  result
            bne  _return
_return_minusone
            ldy  #-1
            sty  result
_return
        }}

        return result
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
