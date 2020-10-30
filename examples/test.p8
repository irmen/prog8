%import textio
%import floats
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        float[]  fls = [1.1, 2.2, 0.0, 4.4, 3.3]
        float fl
        ubyte ii


        fls[2] = sin(fls[0])
        for ii in 0 to len(fls)-1 {
            floats.print_f(fls[ii])
            txt.chrout('\n')
        }
        txt.chrout('\n')

        fls[3] = cos(fls[0])
        for ii in 0 to len(fls)-1 {
            floats.print_f(fls[ii])
            txt.chrout('\n')
        }


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
