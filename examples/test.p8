%import textio
%import syslib
%zeropage basicsafe


main {

    sub start() {

        repeat 0 {
            txt.print("repeat0\n")
        }

        repeat 2 {
            txt.print("repeat2\n")
        }

        ubyte u=3

        repeat u-1 {
            txt.print("repeat u=2\n")
        }

        u=2
        repeat u-1 {
            txt.print("repeat u=1\n")
        }

        u=1
        repeat u-1 {
            txt.print("repeat u=0\n")
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



