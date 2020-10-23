%import textio
%import floats
%option enable_floats       ; TODO remove this option, only import floats is requires
%zeropage basicsafe

main {

    sub start() {
        ubyte char = c64.CHRIN()
        ubyte char2 = chrin()
        uword ssss = getstr()
        float fl = getfloat()

        char++
        char2++
        testX()
        ;char=strlen(ssss)
    }

    sub chrin() -> ubyte {
        return 99
    }

    sub getstr() -> str {
        return "foo"
    }

    sub getfloat() -> float {
        return 4.56789
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
