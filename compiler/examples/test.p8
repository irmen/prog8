%import c64utils
%import mathlib
%option enable_floats

~ main {

            ;c64scr.PLOT(screenx(x), screeny(y))    ; @todo fix argument calculation???!!!

    ; @todo unify the type cast functions...   "wrd(5)"  ->  "5 as word"

    sub toscreenx(float x, float z) -> word {
        return 42
    }

    asmsub blerp(ubyte x @ A, uword ding @ XY) -> clobbers() -> () {

    }

    sub start()  {

        word x = toscreenx(1.22, 3.22)
        blerp(4, 555)


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
;        c64scr.print_ubyte(ub1)
;        c64.CHROUT('\n')
;        ub1 = b2ub(fintb(x/4.2 * flt(width)) + width//2)
;        c64scr.print_ubyte(ub1)
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

