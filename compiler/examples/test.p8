%import c64utils
%import mathlib
%option enable_floats

~ main {

            ;c64scr.PLOT(screenx(x), screeny(y))    ; @todo fix argument calculation???!!!

    sub start()  {


        byte[4] ba = [-1,2,-10,30]
        ubyte[4] uba = [4,200,10,15]
        word[5] wa = [400,-200,-1000,9999,1500]
        uword[7] uwa = [333,42,9999,12,150,1000,4000]
        float[6] fa = [-2.22, 3.33, -5.55, 1.11, 9999.99, -999.99]
        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        byte bmin = min(ba)
        byte bmax = max(ba)
        ubyte ubmin = min(uba)
        ubyte ubmax = max(uba)
        word wmin = min(wa)
        word wmax = max(wa)
        uword uwmin = min(uwa)
        uword uwmax = max(uwa)
        float fmin = min(fa)
        float fmax = max(fa)

        c64scr.print_w(wmin)
        c64.CHROUT(',')
        c64scr.print_w(wmax)
        c64.CHROUT('\n')
        c64scr.print_uw(uwmin)
        c64.CHROUT(',')
        c64scr.print_uw(uwmax)
        c64.CHROUT('\n')

        c64scr.print_b(bmin)
        c64.CHROUT(',')
        c64scr.print_b(bmax)
        c64.CHROUT('\n')
        c64scr.print_ub(ubmin)
        c64.CHROUT(',')
        c64scr.print_ub(ubmax)
        c64.CHROUT('\n')

        c64flt.print_f(fmin)
        c64.CHROUT(',')
        c64flt.print_f(fmax)
        c64.CHROUT('\n')

        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

;        ub3 = 200/67 as ubyte
;        ub3 = 200//67
;        c64scr.print_ub(ub3)
;        c64.CHROUT('\n')
;        ub3 = ub1/ub2
;        c64scr.print_ub(ub3)
;        c64.CHROUT('\n')
;        ub3 = ub1//ub2
;        c64scr.print_ub(ub3)
;        c64.CHROUT('\n')
;
;        uw3 = 2000/67 as uword
;        c64scr.print_uw(uw3)
;        c64.CHROUT('\n')
;        uw3 = 2000//67
;        c64scr.print_uw(uw3)
;        c64.CHROUT('\n')
;        uw3 = uw1/uw2
;        c64scr.print_uw(uw3)
;        c64.CHROUT('\n')
;        uw3 = uw1//uw2
;        c64scr.print_uw(uw3)
;        c64.CHROUT('\n')
;

;        const byte width=20
;        word   w1
;        byte   b1
;        ubyte   ub1
;        float x = 3.45
;        b1 = fintb(x * flt(width)/4.2) + width//2
;        c64scr.print_b(b1)
;        c64.CHROUT('\n')
;        b1 = fintb(x/4.2 * flt(width)) + width//2
;        c64scr.print_b(b1)
;        c64.CHROUT('\n')
;        ub1 = b2ub(fintb(x * flt(width)/4.2) + width//2)
;        c64scr.print_ub(ub1)
;        c64.CHROUT('\n')
;        ub1 = b2ub(fintb(x/4.2 * flt(width)) + width//2)
;        c64scr.print_ub(ub1)
;        c64.CHROUT('\n')
;        w1 = fintw(x * flt(width)/4.2) + width//2
;        c64scr.print_w(w1)
;        c64.CHROUT('\n')
;        w1 = fintw(x/4.2 * flt(width)) + width//2
;        c64scr.print_w(w1)
;        c64.CHROUT('\n')
        ;uw1 = w2uw(fintw(x * flt(width)/4.2) + width//2)       ; @todo w2uw

    }
}

