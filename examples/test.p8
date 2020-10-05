%import textio
%import syslib
%zeropage basicsafe


main {

    str planet_name = "12345678"
    sub start() {

        ; TODO make this work, with a warning about Pc:
        ubyte status
        status = c64.OPEN()          ; open 1,8,0,"$"
        ; TODO make this work as well, with the same warning:
        ubyte status2 = c64.OPEN()          ; open 1,8,0,"$"



        txt.print(planet_name)
        txt.chrout('\n')

        planet_name = "saturn"

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



