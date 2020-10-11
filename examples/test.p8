%import textio
%import syslib
%zeropage basicsafe


main {

    sub start() {

        txt.print("\nCommands are:\n"+
            "buy   jump      inf     cash\n" +
            "sell  teleport  market  hold\n" +
            "fuel  galhyp    local   quit\n")

;        str name = "irmen de jong"
;        uword strptr = &name
;
;
;        txt.print_ub(strlen("1234"))
;        txt.chrout('\n')
;        txt.print_ub(strlen(name))
;        txt.chrout('\n')
;        txt.print_uwhex(strptr, 1)
;        txt.chrout('\n')
;        txt.print(strptr)
;        txt.chrout('\n')
;        txt.print_ub(strlen(strptr))
;        txt.chrout('\n')


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



