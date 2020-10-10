%import textio
%import syslib
%zeropage basicsafe


main {

    sub start() {

        const ubyte c1 = 111
        const ubyte c2 = 122
        const ubyte c3 = 133
        ubyte ii


        for ii in [c1, c2, c3] {
            txt.print_ub(ii)
        }

;        ubyte[3] numbers
;
;        for ii in [55,44,33] {
;            txt.print_ub(ii)
;        }

;        ubyte a = 99
;        ubyte b = 88
;        ubyte c = 77
;
;        ;numbers = numbers       ; TODO optimize away
;
;        numbers = [c1,c2,c3]
;        numbers = [1,2,3]
;        numbers = [a,b,c]
;
;        ubyte[] a1 = [c1,c2,c3]
;        ubyte[3] a2
;
;        a2 = [a,b,c]
;
;        txt.print_ub(a)
;        txt.print_ub(b)
;        txt.print_ub(c)
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



