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
    uword[] colors = [ c1, c2, c3]      ; TODO should contain pointers to (the first element) of each struct


    sub start() {

        ubyte a
        ubyte b
        ubyte c

        c = a==b

        Color c1 = [11,22,33]
        Color c2 = [11,22,33]
        Color c3 = [11,22,33]
        uword[] colors = [ c1, c2, c3]      ; TODO should contain pointers to (the first element) of each struct

        c1 = c2
        ; c1 = [11,22,33]         ; TODO implement rewrite into individual struct member assignments
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
