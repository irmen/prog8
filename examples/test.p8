%import textio
%import syslib
%import floats
%zeropage basicsafe


main {

    sub start() {

        str name = "irmen de jong"
        uword strptr = &name


        txt.print_ub(strlen("1234"))
        txt.chrout('\n')
        txt.print_ub(strlen(name))
        txt.chrout('\n')
        txt.print_uwhex(strptr, 1)
        txt.chrout('\n')
        txt.print(strptr)
        txt.chrout('\n')
        txt.print_ub(strlen(strptr))
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



