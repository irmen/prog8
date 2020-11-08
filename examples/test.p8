%import textio
%import floats
%import syslib
%zeropage basicsafe

main {

    sub start() {
        float t = 0.0

        repeat 10 {
            float cosa = cos(t)
            float sina = sin(t)
            float cosb = cos(t*0.33)
            float sinb = sin(t*0.33)
            float cosc = cos(t*0.78)
            float sinc = sin(t*0.78)
        }

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
