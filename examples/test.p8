%import textio
%import syslib
%zeropage basicsafe


main {

    str planet_name = "12345678"
    sub start() {

        ; TODO : make str name=... work (although it's doing something else namely a strcpy)

        txt.print(planet_name)
        txt.chrout('\n')

        planet_name = "saturn"   ; TODO make strcpy() actually work it now sets the address in the first two bytes...

        txt.print(planet_name)
        txt.chrout('\n')
        txt.print_ub(len(planet_name))
        txt.chrout('\n')
        txt.print_ub(strlen(planet_name))
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



