%import textio
%import syslib
%zeropage basicsafe


main {

    str planet_name = "12345678"
    sub start() {

        c64.OPEN()          ; works: function call droppign the value but preserving the statusregister
        if_cs
            return

        ubyte status
        status = c64.OPEN()          ; open 1,8,0,"$"
        if_cs
            return


        ; TODO make this work as well, with the same warning:
        ubyte status2 = c64.OPEN()          ; open 1,8,0,"$"
        if_cs
            return

        txt.print_ub(status)
        txt.print_ub(status2)

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



