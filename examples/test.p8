%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        ubyte ub = 1
        ubyte ub2 = %11000011

        @($c001) = %1

        @($c000+ub) += @($c000+ub)
        ub2 = @($c000+ub)
        txt.print_ubbin(ub2, 1)
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
