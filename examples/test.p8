%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        float[]  array = [1111.1,2222.2,3333.3,4444.4,5555.5]

        float fw
        ubyte i1 = 1
        ubyte i2 = 3
        ubyte zero = 0
        ubyte four = 4
        swap(array[i1], array[0])
        swap(array[4], array[i2])

        for i1 in 0 to len(array)-1 {
            floats.print_f(array[i1])
            txt.chrout(',')
        }
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
