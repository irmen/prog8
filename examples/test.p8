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

        str s1 = "a\nb\nc\nd\n"
        str s2 = "a\rb\rc\rd\n"

        txt.print(s2)
        txt.print(s2)

        ubyte cc
        for cc in s1 {
            txt.print_ubhex(cc, false)
            txt.chrout(' ')
        }
        txt.chrout('\n')
        for cc in s2 {
            txt.print_ubhex(cc, false)
            txt.chrout(' ')
        }
        txt.chrout('\n')

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



