%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        word ww
        uword uw
        ubyte ub
        byte bb
        ub = 5

        uw = 21111
        uw <<= ub+1
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+2
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+3
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+4
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+5
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+6
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+7
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+8
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+9
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+10
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+11
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw <<= ub+12
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')

        testX()
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
