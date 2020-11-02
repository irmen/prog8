%import textio
%import floats
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        const uword ADDR = $0400

        byte zerob=0
        word zerow=0
        float zerof=0
        byte bb
        word ww
        float fl

        testX()

        bb = -100
        bb = zerob+abs(bb)
        txt.print_b(bb)
        txt.chrout('\n')

        ww = -12345
        ww = zerow+abs(ww)
        txt.print_w(ww)
        txt.chrout('\n')

        fl = -9.876
        fl = zerof+abs(fl)
        floats.print_f(fl)
        txt.chrout('\n')

;        memset(ADDR, 40*25, 100)
;        memsetw(ADDR, 20*10, $3031)
;        memcopy(ADDR, ADDR+40*12, 20*10*2)

        testX()

        bb++
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
