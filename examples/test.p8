%import textio
%import floats
%import syslib
%zeropage basicsafe

; builtin functions converted to new call convention:
; all functions operating on floating-point and fp arrays.
;
; mkword
; lsb
; msb


main {

    sub start() {
        ubyte[]  barr = [1,2,3,4,5,0,4,3,2,1]

        ubyte zero=0
        ubyte ub
        ubyte ub2
        byte bb
        uword uw

        ub = $fe
        ub2 = $34
        uw = mkword(ub, ub2)
        txt.print_uwhex(uw, true)
        txt.chrout('\n')
        uw = zero+mkword(ub, ub2)*1+zero
        txt.print_uwhex(uw, true)
        txt.chrout('\n')

        uw = $fe34
        ub = msb(uw)
        txt.print_ubhex(ub, true)
        txt.chrout('\n')
        ub = zero+msb(uw)*1+zero
        txt.print_ubhex(ub, true)
        txt.chrout('\n')

        uw = $fe34
        ub = lsb(uw)
        txt.print_ubhex(ub, true)
        txt.chrout('\n')
        ub = zero+lsb(uw)*1+zero
        txt.print_ubhex(ub, true)
        txt.chrout('\n')




;        ub = min(barr)
;
;        ub = zero+min(barr)*1+zero
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = max(barr)
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = zero+max(barr)*1+zero
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        uw = sum(barr)
;        txt.print_uw(uw)
;        txt.chrout('\n')
;
;        uw = zero+sum(barr)*1+zero
;        txt.print_uw(uw)
;        txt.chrout('\n')
;
;        ub = any(barr)
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = zero+any(barr)*1
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = all(barr)
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = zero+all(barr)*1
;        txt.print_ub(ub)
;        txt.chrout('\n')




        testX()
    }

    sub floatingpoint() {
        ubyte[]  barr = [1,2,3,4,5,0,4,3,2,1]
        float[] flarr = [1.1, 2.2, 3.3, 0.0, -9.9, 5.5, 4.4]

        ubyte zero=0
        ubyte ub
        byte bb
        uword uw
        float fl
        float fzero=0.0

        fl = -9.9
        fl = abs(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = -9.9
        fl = fzero+abs(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = 9.9
        fl = atan(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 9.9
        fl = fzero+atan(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = -9.9
        fl = ceil(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = -9.9
        fl = fzero+ceil(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = -9.9
        fl = cos(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = -9.9
        fl = fzero+cos(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = -9.9
        fl = sin(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = -9.9
        fl = fzero+sin(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = 9.9
        fl = tan(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 9.9
        fl = fzero+tan(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = 3.1415927
        fl = deg(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 3.1415927
        fl = fzero+deg(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = 90
        fl = rad(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 90
        fl = fzero+rad(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = -9.9
        fl = floor(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = -9.9
        fl = fzero+floor(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = 3.1415927
        fl = ln(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 3.1415927
        fl = fzero+ln(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = 3.1415927
        fl = log2(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 3.1415927
        fl = fzero+log2(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = -9.9
        fl = round(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = -9.9
        fl = fzero+round(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = -9.9
        bb = sgn(fl)
        txt.print_b(bb)
        txt.chrout('\n')
        fl = -9.9
        bb = zero+sgn(fl)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        fl = 3.1415927
        fl = sqrt(fl)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = 3.1415927
        fl = fzero+sqrt(fl)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        fl = rndf()
        floats.print_f(fl)
        txt.chrout('\n')
        fl = fzero+rndf()*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

        swap(fl, fzero)
        swap(fzero, fl)

        ub = any(flarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+any(flarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = all(flarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+all(flarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        reverse(flarr)
        for ub in 0 to len(flarr)-1 {
            floats.print_f(flarr[ub])
            txt.chrout(',')
        }
        txt.chrout('\n')
        fl = max(flarr)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = fzero+max(flarr)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')
        fl = min(flarr)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = fzero+min(flarr)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')
        fl = sum(flarr)
        floats.print_f(fl)
        txt.chrout('\n')
        fl = fzero+sum(flarr)*1.0+fzero
        floats.print_f(fl)
        txt.chrout('\n')

;        ub = min(barr)
;
;        ub = zero+min(barr)*1+zero
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = max(barr)
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = zero+max(barr)*1+zero
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        uw = sum(barr)
;        txt.print_uw(uw)
;        txt.chrout('\n')
;
;        uw = zero+sum(barr)*1+zero
;        txt.print_uw(uw)
;        txt.chrout('\n')
;
;        ub = any(barr)
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = zero+any(barr)*1
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = all(barr)
;        txt.print_ub(ub)
;        txt.chrout('\n')
;
;        ub = zero+all(barr)*1
;        txt.print_ub(ub)
;        txt.chrout('\n')




        testX()
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
