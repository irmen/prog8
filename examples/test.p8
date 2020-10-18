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

        uw = 21111
        uw >>= 9        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 10        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 11        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 12       ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 13        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 14        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 15        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 16        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
        uw = 21111
        uw >>= 17        ; TODO fix this shift!
        txt.print_uwbin(uw as uword, true)
        txt.chrout('\n')
;        ub >>= 7
;        ub <<= 7
;
;        uu >>=7
;        uu <<= 7
;
;        zz >>=7
;        zz <<=7
;
;        bb >>=7
;        bb <<=7

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
