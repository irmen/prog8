%import textio
%import syslib
%import floats
%zeropage basicsafe


main {

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

    Color c1 = [11,22,33]
    Color c2 = [11,22,33]
    Color c3 = [11,22,33]
    uword[] colors = [ c1, c2, c3]


    sub start() {

        txt.print_ub(c1.red)
        txt.chrout('\n')
        txt.print_ub(c1.green)
        txt.chrout('\n')
        txt.print_ub(c1.blue)
        txt.chrout('\n')
        txt.chrout('\n')

        c1 = [99,88,77]

        txt.print_ub(c1.red)
        txt.chrout('\n')
        txt.print_ub(c1.green)
        txt.chrout('\n')
        txt.print_ub(c1.blue)
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
