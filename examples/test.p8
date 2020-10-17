%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        ubyte[] array = [1,2,3,4]

        const ubyte ic = 2
        ubyte ib = 2

        ib = array[ic+1]
        ib = array[ib]
        ib = array[ib+1]
        ib = array[ib+2]
        ib = array[ib-1]
        ib = array[ib-2]
        ib = array[ib*2]
        ib = array[2*ib]
        ib = array[ib*3]
        ib = array[3*ib]
        ib = array[ib*4]
        ib = array[4*ib]
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
