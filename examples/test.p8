%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        ubyte ub = 4
        uword uw = 5

        when ub {
            1 -> txt.chrout('1')
            2 -> txt.chrout('2')
            3 -> txt.chrout('3')
            4 -> txt.chrout('4')
            else -> txt.chrout('?')
        }
        txt.chrout('\n')

        when uw {
            $0001 -> txt.chrout('1')
            $0002 -> txt.chrout('2')
            $0003 -> txt.chrout('3')
            $0004 -> txt.chrout('4')
            $0005 -> txt.chrout('5')
            else -> txt.chrout('?')
        }

        txt.chrout('\n')

        testX()
    }

    asmsub setflag(ubyte bitje @ Pc) {
        %asm {{
            bcs  +
            lda  #'0'
            jsr  c64.CHROUT
            lda  #13
            jmp  c64.CHROUT
+           lda  #'1'
            jsr  c64.CHROUT
            lda  #13
            jmp  c64.CHROUT
        }}
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
