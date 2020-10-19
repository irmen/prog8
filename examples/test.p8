%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        uword vv  = 1111
        vv *= 23
        txt.print_uw(vv)
        txt.chrout('\n')

        word ww  = -1111
        ww *= 23
        txt.print_w(ww)
        txt.chrout('\n')
        ww  = -1111
        ww *= -23
        txt.print_w(ww)
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
