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

        txt.print_uwhex(&c1, true)
        txt.chrout('\n')
        txt.print_uwhex(&c2, true)
        txt.chrout('\n')
        txt.print_uwhex(&c3, true)
        txt.chrout('\n')
        txt.print_uwhex(colors[0], true)
        txt.chrout('\n')
        txt.print_uwhex(colors[1], true)
        txt.chrout('\n')
        txt.print_uwhex(colors[2], true)
        txt.chrout('\n')

        c1 = c2
        c1 = [11,22,33]         ; TODO implement rewrite into individual struct member assignments
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
