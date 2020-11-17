%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {

        ubyte ff = 10

        setflag(ff-10)
        setflag(ff-9)
        setflag(ff-10)
        setflag(ff-9)

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
