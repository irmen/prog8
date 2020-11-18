%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        uword uw =  %1111111110000001
        uword uw2 = %000111100001110
        ubyte ub = %00001110

        uw &= ub + 1
        txt.print_uwbin(uw, 0)
        txt.chrout('\n')
        uw |= ub+1
        txt.print_uwbin(uw, 0)
        txt.chrout('\n')

        uw ^= ub+1
        txt.print_uwbin(uw, 0)
        txt.chrout('\n')

        testX()
    }

    sub ding(ubyte argument) -> ubyte {
        txt.chrout(' ')
        return argument*2
    }

    sub dingw(uword argument) -> uword {
        txt.chrout(' ')
        return argument*2
    }

    asmsub testX() {
        %asm {{
            stx  _saveX
            lda  #13
            jsr  txt.chrout
            lda  #'x'
            jsr  txt.chrout
            lda  #'='
            jsr  txt.chrout
            lda  _saveX
            jsr  txt.print_ub
            lda  #' '
            jsr  txt.chrout
            lda  #'s'
            jsr  txt.chrout
            lda  #'p'
            jsr  txt.chrout
            lda  #'='
            jsr  txt.chrout
            tsx
            txa
            jsr  txt.print_ub
            lda  #13
            jsr  txt.chrout
            ldx  _saveX
            rts
_saveX   .byte 0
        }}
    }
}
