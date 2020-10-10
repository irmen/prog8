%import textio
%import syslib
%zeropage basicsafe


main {

    sub start() {

        ; TODO fix multi- string concatenation:
        txt.print("\nCommands are:\n"+
            "buy   jump      inf     cash\n" +
            "sell  teleport  market  hold\n" +
            "fuel  galhyp    local   quit\n")


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



