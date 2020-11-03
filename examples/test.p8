%import textio
%import floats
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        const uword ADDR = $0400

        ubyte ubb
        ubyte zerobb
        uword zeroww

        for ubb in 0 to 255 {
            txt.print_uw(sin16u(ubb)+zerobb)
            txt.chrout(' ')
        }
            txt.chrout('\n')
            txt.chrout('\n')
        for ubb in 0 to 255 {
            txt.print_uw(cos16u(ubb)+zerobb)
            txt.chrout(' ')
        }
            txt.chrout('\n')
            txt.chrout('\n')
        for ubb in 0 to 255 {
            txt.print_w(sin16(ubb)+zerobb)
            txt.chrout(' ')
        }
            txt.chrout('\n')
            txt.chrout('\n')
        for ubb in 0 to 255 {
            txt.print_w(cos16(ubb)+zerobb)
            txt.chrout(' ')
        }
            txt.chrout('\n')
            txt.chrout('\n')

        testX()

        return


        byte zerob=0
;        word zerow=0
;        float zerof=0
        byte bb
;        word ww
;        float fl

        testX()

        bb = -100
        bb = zerob+abs(bb)      ; TODO optimizer generates wrong code for this (wrong order of splitted expression?)
        txt.print_b(bb)
        txt.chrout('\n')

;        ww = -12345
;        ww = zerow+abs(ww)      ; TODO optimizer generates wrong code for this (wrong order of splitted expression?)
;        txt.print_w(ww)
;        txt.chrout('\n')
;
;        fl = -9.876
;        fl = zerof+abs(fl)      ; TODO optimizer generates wrong code for this (wrong order of splitted expression?)
;        floats.print_f(fl)
;        txt.chrout('\n')

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
