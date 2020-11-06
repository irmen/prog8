%import textio
%import floats
%import syslib
%zeropage basicsafe

; builtin functions converted to new call convention:
;
; all functions operating on floating-point and fp arrays.
;
; mkword
; lsb
; msb
; swap

; abs
; sgn
; sqrt16
; rnd, rndw
; sin8, sin8u, cos8, cos8u
; sin16, sin16u, cos16, cos16u
; max, min
; any, all
; sum
; sort
; reverse

; exit
; read_flags
; memset, memsetw, memcopy
; leftstr, rightstr, substr
; strlen, strcmp

; TODO: in-place rols and rors

main {

    sub start() {
        const uword ADDR = $0400
        const uword ADDR2 = $4000

;        memset(ADDR2, 40*25, '*')
;        memset(ADDR2, 40, '1')
;        memset(ADDR2+24*40, 39, '2')
;        memsetw(ADDR2, 40*25/2, $3132)
;        memsetw(ADDR2, 20, $4142)
;        memsetw(ADDR2+24*40, 19, $4241)
;        memcopy(ADDR2, ADDR, 200)

        str result = "?" *10
        str s1 = "irmen"
        str s2 = "hello"
        str dots = "....."

        ubyte ub
        byte bb
        ubyte zero=0

        bb = strcmp(s1, s2)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = strcmp(s2, s1)
        txt.print_b(bb)
        txt.chrout('\n')
        txt.print_ub(s1==s2)
        txt.chrout('\n')
        txt.print_ub(s1<s2)
        txt.chrout('\n')
        txt.print_ub(s1>s2)
        txt.chrout('\n')
        bb = zero+strcmp(s1,s2)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+strcmp(s2,s1)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        ub = strlen(s1)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+strlen(s1)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        leftstr(s1, result, 3)
        txt.print(result)
        txt.chrout('\n')
        leftstr(s1, result, len(s1))
        txt.print(result)
        txt.chrout('\n')
        txt.chrout('\n')

        result = "x"*8
        rightstr(s2, result, 3)
        txt.print(result)
        txt.chrout('\n')
        rightstr(s2, result, len(s1))
        txt.print(result)
        txt.chrout('\n')

        result = "y"*10
        substr(s2, result, 1, 3)
        txt.print(result)
        txt.chrout('\n')

        testX()

    }

    sub integers() {
        ubyte[]  ubarr = [1,2,3,4,5,0,4,3,2,1, 255, 255, 255]
        byte[]  barr = [1,2,3,4,5,-4,0,-3,2,1, -128, -128, -127]
        uword[]  uwarr = [100,200,300,400,0,500,400,300,200,100]
        word[] warr = [100,200,300,400,500,0,-400,-300,200,100,-99, -4096]

        ubyte zero=0
        ubyte ub
        ubyte ub2
        byte bb
        uword uw
        word ww

        repeat(20) {
            txt.chrout('\n')
        }

        ub = read_flags()
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+read_flags()*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')


        ub = rnd()
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+rnd()*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        uw = rndw()
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+rndw()*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')


        uw = 50000
        ub = sqrt16(uw)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+sqrt16(uw)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        bb = -100
        bb = sgn(bb)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = -100
        bb = zero+sgn(bb)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        ub = 100
        bb = sgn(ub)
        txt.print_b(bb)
        txt.chrout('\n')
        ub = 100
        bb = zero+sgn(ub)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        ww = -1000
        bb = sgn(ww)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+sgn(ww)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        uw = 1000
        bb = sgn(uw)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+sgn(uw)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        ub = 0
        uw = sin16u(ub)
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+sin16u(ub)*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')

        ub = 0
        uw = cos16u(ub)
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+cos16u(ub)*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')

        ub = 0
        ww = sin16(ub)
        txt.print_w(ww)
        txt.chrout('\n')
        ww = zero+sin16(ub)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        ub = 0
        ww = cos16(ub)
        txt.print_w(ww)
        txt.chrout('\n')
        uw = 0
        ww = zero+cos16(ub)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        ub2 = 0
        ub = sin8u(ub2)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+sin8u(ub2)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub2 = 0
        ub = cos8u(ub2)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+cos8u(ub2)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub2 = 0
        bb = sin8(ub2)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+sin8(ub2)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        ub2 = 0
        bb = cos8(ub2)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+cos8(ub2)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        bb = -100
        bb = abs(bb)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = -100
        bb = zero+abs(bb)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        ww = -1000
        ww = abs(ww)
        txt.print_w(ww)
        txt.chrout('\n')
        ww = -1000
        ww = zero+abs(ww)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        ub = min(ubarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+min(ubarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        bb = min(barr)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+min(barr)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        uw = min(uwarr)
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+min(uwarr)*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')

        ww = min(warr)
        txt.print_w(ww)
        txt.chrout('\n')
        ww = zero+min(warr)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        ub = max(ubarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+max(ubarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        bb = max(barr)
        txt.print_b(bb)
        txt.chrout('\n')
        bb = zero+max(barr)*1+zero
        txt.print_b(bb)
        txt.chrout('\n')

        uw = max(uwarr)
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+max(uwarr)*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')

        ww = max(warr)
        txt.print_w(ww)
        txt.chrout('\n')
        ww = zero+max(warr)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        ub = any(ubarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+any(ubarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = any(barr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+any(barr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = any(uwarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+any(uwarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = any(warr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+any(warr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = all(ubarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+all(ubarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = all(barr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+all(barr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = all(uwarr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+all(uwarr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')

        ub = all(warr)
        txt.print_ub(ub)
        txt.chrout('\n')
        ub = zero+all(warr)*1+zero
        txt.print_ub(ub)
        txt.chrout('\n')


        uw = sum(ubarr)
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+sum(ubarr)*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')

        ww = sum(barr)
        txt.print_w(ww)
        txt.chrout('\n')
        ww = zero+sum(barr)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        uw = sum(uwarr)
        txt.print_uw(uw)
        txt.chrout('\n')
        uw = zero+sum(uwarr)*1+zero
        txt.print_uw(uw)
        txt.chrout('\n')

        ww = sum(warr)
        txt.print_w(ww)
        txt.chrout('\n')
        ww = zero+sum(warr)*1+zero
        txt.print_w(ww)
        txt.chrout('\n')

        sort(ubarr)
        sort(barr)
        sort(uwarr)
        sort(warr)
        reverse(ubarr)
        reverse(barr)
        reverse(uwarr)
        reverse(warr)

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
