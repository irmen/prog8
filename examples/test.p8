%import textio
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        uword num
        ubyte ss

        num = 65535
        ss = sqrt16(num)
        txt.print_ub(ss)
        txt.chrout('\n')

        num = 20000
        ss = sqrt16(num)
        txt.print_ub(ss)
        txt.chrout('\n')

        num = 9999
        ss = sqrt16(num)
        txt.print_ub(ss)
        txt.chrout('\n')

        num = 500
        ss = sqrt16(num)
        txt.print_ub(ss)
        txt.chrout('\n')

        testX()
    }

    sub chrin() -> ubyte {
        return 99
    }

    sub getstr() -> str {
        @($d020)++
        return "foobar"
    }

    sub getfloat() -> float {
        return 123.456789
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
