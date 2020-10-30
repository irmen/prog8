%import textio
%import floats
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        str string1 = "abcdef"
        str string2 = "%=&"
        uword sa

        txt.print(string1)
        txt.chrout('\n')
        string1=string2
        txt.print(string1)
        txt.chrout('\n')

        void getstr()

        sa = getstr()
        txt.print_uwhex(sa, true)
        txt.chrout('\n')

        string1 = getstr()
        txt.print(string1)
        txt.chrout('\n')

;        fl = getfloat()
;
;        floats.print_f(fl)
;        txt.chrout('\n')

        testX()
    }

    sub chrin() -> ubyte {
        return 99
    }

    sub getstr() -> str {
        @($d020)++
        return "foobar"
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
