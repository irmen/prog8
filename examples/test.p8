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

;        byte result
;        result = strcmp(hex1, hex1)
;        txt.print_b(result)
;        txt.chrout('\n')
;        result = strcmp(hex1, hex1)
;        txt.print_b(result)
;        txt.chrout('\n')
;        result = strcmp(hex1, hex2)
;        txt.print_b(result)
;        txt.chrout('\n')
;        result = strcmp(hex1, hex3)
;        txt.print_b(result)
;        txt.chrout('\n')
;        result = strcmp(hex1, hex4)
;        txt.print_b(result)
;        txt.chrout('\n')
;        txt.chrout('\n')

        if hex1==hex2
            txt.print("1 fail ==\n")
        else
            txt.print("1 ok not ==\n")
endlab1:
        if hex1!=hex2
            txt.print("2 ok !==\n")
        else
            txt.print("2 fail not !=\n")
endlab2:
        if hex1>=hex2
            txt.print("3 ok >=\n")
        else
            txt.print("3 fail not >=\n")
endlab3:
        if hex1<=hex2
            txt.print("4 fail <=\n")
        else
            txt.print("4 ok not <=\n")
endlab4:
        if hex1>hex2
            txt.print("5 ok >\n")
        else
            txt.print("5 fail not >\n")
endlab5:
        if hex1<hex2
            txt.print("5 fail <\n")
        else
            txt.print("6 ok not <\n")

endlab6:
        txt.chrout('\n')

        txt.print_ub(hex1==hex2)
        txt.print("  0?\n")
        txt.print_ub(hex1!=hex2)
        txt.print("  1?\n")
        txt.print_ub(hex1>hex2)
        txt.print("  1?\n")
        txt.print_ub(hex1<hex2)
        txt.print("  0?\n")
        txt.print_ub(hex1>=hex2)
        txt.print("  1?\n")
        txt.print_ub(hex1<=hex2)
        txt.print("  0?\n")

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
