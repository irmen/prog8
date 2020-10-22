%import textio
%import diskio
%zeropage basicsafe

main {

    sub start() {
        memcopy($aaaa, $bbbb, 200)
        mcp($aaaa, $bbbb, 200)
        testX()
    }

    sub mcp(uword from, uword dest, ubyte length) {
        txt.print_uw(from)
        txt.print_uw(dest)
        txt.print_ub(length)
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
