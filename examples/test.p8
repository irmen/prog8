%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        uword[]  array = [1, 2, 3]
        ubyte ii = 0
        ubyte ii2 = ii+2
        array[ii+1] = array[ii2]           ; TODO fix overwriting the single array index autovar

        uword xx
        for xx in array {
            txt.print_uw(xx)
            txt.chrout('\n')
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
