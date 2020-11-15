%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        float f = 1.1
        float[] farr = [2.2, 3.3]

        floats.print_f(f)
        txt.chrout('\n')
        floats.print_f(farr[0])
        txt.chrout(',')
        floats.print_f(farr[1])
        txt.chrout('\n')

        swap(f, farr[1])
        floats.print_f(f)
        txt.chrout('\n')
        floats.print_f(farr[0])
        txt.chrout(',')
        floats.print_f(farr[1])
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
