%import textio
%import syslib
%import floats
%zeropage basicsafe


main {

    sub start() {
        derp()
        rec2()
    }

    sub rec2() {
        rec3()
    }

    sub rec3() {
        rec4()
    }

    sub rec4() {
        rec2()
    }

    sub derp() {
        repeat {
            derp()
        }
        if true {
            derp()
        } else {
            derp()
        }

        do {
            derp()
        } until true

        while true {
            derp()
        }
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



