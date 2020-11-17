%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        ubyte ub = 4
        uword uw = 5

        if ding(22)!=0
            txt.chrout('1')
        if ding(22)
            txt.chrout('2')
        txt.chrout('\n')

        if dingw($1100)!=$0000
            txt.chrout('1')
        if dingw($1100)
            txt.chrout('2')
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
