%import c64utils
%option enable_floats

~ main {

    sub start()  {

        ubyte ub1
        ubyte ub2
        byte b1 = -99
        byte b2
        uword uw1
        uword uw2
        word  w1  = -9999
        word  w2
        float f1
        float f2
        float f3

        ubyte[3]  uba = [1,2,3]
        byte[3]   ba = [-1,0,3]
        uword[3]  uwa = [1000,200,0]
        word[3]   wa = -222
        ubyte[3]  uba0 = 0
        byte[3]   ba0 = 0
        uword[3]  uwa0 = 0
        word[3]   wa0 = 0
        ;word[3]   wa = [-1000.w,2000.w,3000.w]      ; @todo array data type fix (float->word)
        ;word[3]   wa = [1000,2000,3000]      ; @todo array data type fix (uword->word)
        float[3]  fa0 = 0.0
        float[3]  fa1 = [-1000,44.555, 99.999]
        float[3]  fa2 = [-1000,44.555, 0]
        str string = "hello"
        str_p pstring = "hello1"
        str_s sstring = "hello12"
        str_ps psstring = "hello123"

        c64.CHROUT('x')
        c64scr.print_ubyte_decimal(X)
        c64.CHROUT('\n')

        ub1 = any(uba)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(ba)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(uwa)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(wa)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(fa1)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(fa2)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')

        ub1 = any(uba0)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(ba0)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(uwa0)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(wa0)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')
        ub1 = any(fa0)
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')

        c64.CHROUT('x')
        c64scr.print_ubyte_decimal(X)
        c64.CHROUT('\n')

        c64.CHROUT('x')
        c64scr.print_ubyte_decimal(X)
        c64.CHROUT('\n')

;        ub1 = all(uba)      ; 1 ok
;        c64scr.print_ubyte_decimal(ub1)
;        c64.CHROUT('\n')
;        ub1 = all(ba)       ; 0 ok
;        c64scr.print_ubyte_decimal(ub1)
;        c64.CHROUT('\n')
;        ub1 = all(uwa)      ; 0 ok
;        c64scr.print_ubyte_decimal(ub1)
;        c64.CHROUT('\n')
;        ub1 = all(wa)       ; 1 ok
;        c64scr.print_ubyte_decimal(ub1)
;        c64.CHROUT('\n')
;        ub1 = all(fa1)      ; 1 ok
;        c64scr.print_ubyte_decimal(ub1)
;        c64.CHROUT('\n')
;        ub1 = all(fa2)      ; 0 ok
;        c64scr.print_ubyte_decimal(ub1)
;        c64.CHROUT('\n')
;        c64.CHROUT('x')
;        c64scr.print_ubyte_decimal(X)
;        c64.CHROUT('\n')

    }
}

