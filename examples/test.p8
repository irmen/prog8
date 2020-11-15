%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        ubyte[] barr = [%10011111, %10011111]

        rol2(barr[0])
        ror2(barr[1])

        txt.print_ubbin(barr[0],0)
        txt.chrout('\n')
        txt.print_ubbin(barr[1],0)
        txt.chrout('\n')

        testX()
    }

    asmsub testX() {
        %asm {{
            stx  _saveX
            lda  #13
            jsr  txt.chrout
            lda  #'x'
            jsr  txt.chrout
            lda  #'='
            jsr  txt.chrout
            lda  _saveX
            jsr  txt.print_ub
            lda  #' '
            jsr  txt.chrout
            lda  #'s'
            jsr  txt.chrout
            lda  #'p'
            jsr  txt.chrout
            lda  #'='
            jsr  txt.chrout
            tsx
            txa
            jsr  txt.print_ub
            lda  #13
            jsr  txt.chrout
            ldx  _saveX
            rts
_saveX   .byte 0
        }}
    }
}
