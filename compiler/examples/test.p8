%import c64utils
%import mathlib
%option enable_floats

~ main {

            ;c64scr.PLOT(screenx(x), screeny(y))    ; @todo fix argument calculation???!!!

    sub start()  {

        ubyte ub1
        ubyte ub2
        ubyte ub3
        byte b1
        byte b2
        byte b3
        uword uw1
        uword uw2
        uword uw3
        word w1
        word w2
        word w3
        float f1
        float f2
        float f3

        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        ub1=20
        ub2=6
        ub3=ub1*ub2
        c64scr.print_ub(ub3)        ; 120
        c64.CHROUT('\n')
        uw1=900
        uw2=66
        uw3=uw1*uw2
        c64scr.print_uw(uw3)        ; 59400
        c64.CHROUT('\n')

        b1=20
        b2=6
        b3=b1*b2
        c64scr.print_b(b3)          ; 120
        c64.CHROUT('\n')
        w1=500
        w2=44
        w3=w1*w2
        c64scr.print_w(w3)          ; 22000
        c64.CHROUT('\n')
        b1=20
        b2=-6
        b3=b1*b2
        c64scr.print_b(b3)          ; -120
        c64.CHROUT('\n')
        w1=500
        w2=-44
        w3=w1*w2
        c64scr.print_w(w3)          ; -22000
        c64.CHROUT('\n')
        b1=-20
        b2=-6
        b3=b1*b2
        c64scr.print_b(b3)          ; 120
        c64.CHROUT('\n')
        w1=-500
        w2=-44
        w3=w1*w2
        c64scr.print_w(w3)          ; 22000
        c64.CHROUT('\n')
        f1=-500.11
        f2=44.4
        f3=f1*f2
        c64flt.print_f(f3)
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
;        f3 = 999/44
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = 999//44
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = f1/f2
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = f1//f2
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = ub1/ub2
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = ub1//ub2
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = uw1/uw2
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
;        f3 = uw1//uw2
;        c64flt.print_f(f3)
;        c64.CHROUT('\n')
        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

;        const byte width=20
;        word   w1
;        byte   b1
;        ubyte   ub1
;        float x = 3.45
;        b1 = fintb(x * flt(width)/4.2) + width//2
;        c64scr.print_byte(b1)
;        c64.CHROUT('\n')
;        b1 = fintb(x/4.2 * flt(width)) + width//2
;        c64scr.print_byte(b1)
;        c64.CHROUT('\n')
;        ub1 = b2ub(fintb(x * flt(width)/4.2) + width//2)
;        c64scr.print_ub(ub1)
;        c64.CHROUT('\n')
;        ub1 = b2ub(fintb(x/4.2 * flt(width)) + width//2)
;        c64scr.print_ub(ub1)
;        c64.CHROUT('\n')
;        w1 = fintw(x * flt(width)/4.2) + width//2
;        c64scr.print_word(w1)
;        c64.CHROUT('\n')
;        w1 = fintw(x/4.2 * flt(width)) + width//2
;        c64scr.print_word(w1)
;        c64.CHROUT('\n')
        ;uw1 = w2uw(fintw(x * flt(width)/4.2) + width//2)       ; @todo w2uw

    }
}

