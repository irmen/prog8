%import textio
%import floats
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {
    sub start() {

        uword ss

        ss = %1000000110101010
        ss <<= 8
        txt.print_uwbin(ss, 1)
        txt.chrout('\n')

        ss = %1000000110101111
        ss <<= 9
        txt.print_uwbin(ss, 1)
        txt.chrout('\n')

        ss = %1000000110101111
        ss <<= 14
        txt.print_uwbin(ss, 1)
        txt.chrout('\n')

        ss = %1000000110101111
        ss <<= 15
        txt.print_uwbin(ss, 1)
        txt.chrout('\n')

        ss = %1000000110101111
        ss <<= 16
        txt.print_uwbin(ss, 1)
        txt.chrout('\n')

        ss = %1000000110101010
        ss <<= 17
        txt.print_uwbin(ss, 1)
        txt.chrout('\n')

        main22.testX()
    }
}

main22 {

    sub start() {
        ubyte char
        uword ssss
        float fl

        ;char = 1+(lsb(ssss) * 2)
        ;fl = 2.0*(abs(fl) + 1.0)

        char = abs(char)
        char = lsb(ssss)
        char++
        char = msb(ssss)
        char++
        char = c64.CHRIN()

        txt.print_ub(char)
        txt.chrout('\n')

        char = chrin()

        txt.print_ub(char)
        txt.chrout('\n')

        void getstr()
        ; TODO string assign ssss = getstr()

        txt.print_uwhex(ssss, true)
        txt.chrout('\n')

;        fl = getfloat()
;
;        floats.print_f(fl)
;        txt.chrout('\n')

        testX()
        ;char=strlen(ssss)
    }

    sub chrin() -> ubyte {
        return 99
    }

    sub getstr() -> str {
        @($d020)++
        return "foo"
    }

;    sub getfloat() -> float {
;        return 4.56789
;    }

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
