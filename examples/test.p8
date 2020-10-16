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
            goto  endlab1
        else
            txt.print("not ==")
endlab1:
        if hex1!=hex2
            goto  endlab2
        else
            txt.print("not !=")
endlab2:
        if hex1>=hex2
            goto  endlab3
        else
            txt.print("not >=")
endlab3:
        if hex1<=hex2
            goto  endlab4
        else
            txt.print("not <=")
endlab4:
        if hex1>hex2
            goto  endlab5
        else
            txt.print("not >")
endlab5:
        if hex1<hex2
            goto  endlab6
        else
            txt.print("not <")

endlab6:

        txt.print_ub(hex1==hex2)
        txt.chrout('\n')
        txt.print_ub(hex1!=hex2)
        txt.chrout('\n')
        txt.print_ub(hex1>hex2)
        txt.chrout('\n')
        txt.print_ub(hex1<hex2)
        txt.chrout('\n')
        txt.print_ub(hex1>=hex2)
        txt.chrout('\n')
        txt.print_ub(hex1<=hex2)
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
