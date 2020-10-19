%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        uword[]  array = [1, 2, 3]
        uword fzero = 0.0
        uword fnine = 9999
        array[0] = 0
        ubyte ii = 1
        array[ii] = 0

        uword ff
        for ii in 0 to len(array)-1 {
            txt.print_uw(array[ii])
            txt.chrout('\n')
        }

        array[0] = 9
        ii = 1
        array[ii] = 9

        for ii in 0 to len(array)-1 {
            txt.print_uw(array[ii])
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
