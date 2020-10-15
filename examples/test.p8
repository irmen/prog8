%import textio
%import conv
%zeropage basicsafe

main {

    sub start() {

        str num1 = "01234"
        str num2 = @"01234"
        str hex1 = "a04E"
        str hex2 = "$a04E"
        str hex3 = @"a04E"
        str hex4 = @"$a04E"

;        txt.print(num1)
;        txt.chrout('\n')
;        txt.print(num2)
;        txt.chrout('\n')
;        txt.print(hex1)
;        txt.chrout('\n')
;        txt.print(hex2)
;        txt.chrout('\n')
;        txt.print(hex3)
;        txt.chrout('\n')
;        txt.print(hex4)
;        txt.chrout('\n')

        ubyte cc
        for cc in 0 to len(hex3)-1 {
            @($0410+cc) = hex3[cc]
            txt.setchr(16+cc,2,hex3[cc])
        }

        for cc in 0 to len(hex4)-1 {
            @($0420+cc) = hex4[cc]
            txt.setchr(32+cc,2,hex4[cc])
        }

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
