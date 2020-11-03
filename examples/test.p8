%import textio
%import floats
%import syslib
%zeropage basicsafe

; builtin functions converted to new call convention:
; abs
; atan
; ceil
; cos (float), cos8, cos8u, cos16, cos16u
; deg
; floor
; ln
; log2
; rad
; round
; sgn (float), sgn (integers)
; sin (float), sin8, sin8u, sin16, sin16u
; sqrt (float), sqrt16
; tan


main {

    sub start() {
        const uword ADDR = $0400

        ubyte ubb
        ubyte zerobb
        uword zeroww
        uword uww
        word ww
        float fl
        float fzero=0.0
        float rr=0.0

        ; TODO byte -> word conversion is wrong

        rr = 2.0
        for ubb in 0 to 10 {
            floats.print_f(tan(rr))
            txt.chrout('\n')
            rr += 0.5
        }
            txt.chrout('\n')
            txt.chrout('\n')

        rr = 2.0
        for ubb in 0 to 10 {
            floats.print_f(tan(rr)+fzero)
            txt.chrout('\n')
            rr += 0.5
        }
            txt.chrout('\n')
            txt.chrout('\n')

;        for uww in 0 to 50000 step 10000 {
;            txt.print_ub(sqrt16(uww))
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')

;        for ww in -5 to 5 {
;            txt.print_b(sgn(ww)+zerobb)
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')

;        for ww in -2 to 2 {
;            txt.print_b(sgn(ww))
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')
;
;        for ww in -2 to 2 {
;            txt.print_b(sgn(ww)+zerobb)
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')
;
;        for bb in -2 to 2 {
;            txt.print_b(sgn(bb))
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')
;
;        for bb in -2 to 2 {
;            txt.print_b(sgn(bb)+zerobb)
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')


;        for bb in -5 to 5 {
;            txt.print_b(sgn(bb))
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')
;
;        for bb in -5 to 5 {
;            txt.print_b(sgn(bb)+zerobb)
;            txt.chrout('\n')
;        }
;            txt.chrout('\n')
;            txt.chrout('\n')
;


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
