%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        uword vv  = $1111
        uword vv2
        vv2 = vv2+(vv/2)
        vv2 = vv2+(vv - 1)
        vv2 = vv2+(vv + $0200)
        vv2 = vv2+(vv - $0400)
        txt.print_uw(vv2)
        txt.chrout('\n')

        word ww  = -$1111
        word ww2 = 0
        ww2 = ww2 + ww + $0200
        ww2 = ww2 +ww - $0400
        txt.print_w(ww2)
        txt.chrout('\n')
        ww2=  ww2 + ww + -$0200
        ww2= ww2 + ww - -$0400
        txt.print_w(ww2)
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
