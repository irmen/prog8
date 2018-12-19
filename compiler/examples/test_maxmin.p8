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
        ubyte[3]  uba0 = 0
        byte[3]   ba0 = 0
        uword[3]  uwa0 = 0
        word[3]   wa0 = -222
        word[3]   wa1 = [-1000.w,2000.w,3000.w]
        word[3]   wa2 = [1000,2000,3000]
        float[3]  fa0 = 0.0
        float[3]  fa1 = [-1000,44.555, 99.999]
        float[3]  fa2 = [-1000,44.555, 0]
        str string = "hello"
        str_p pstring = "hello1"
        str_s sstring = "hello12"
        str_ps psstring = "hello123"

        c64.CHROUT('x')
        c64scr.print_ubyte(X)
        c64.CHROUT('\n')

        ; @todo implement max and min, AND FIX STACKPTR (X) ERRORS!

        ub1 = max(uba)
        c64scr.print_ubyte(ub1)
        c64.CHROUT('\n')
        b1 = max(ba)
        c64scr.print_byte(b1)
        c64.CHROUT('\n')
        uw1 = max(uwa)
        c64scr.print_uword(uw1)
        c64.CHROUT('\n')
        w1 = max(wa0)
        c64scr.print_word(w1)
        c64.CHROUT('\n')
        w1 = max(wa1)
        c64scr.print_word(w1)
        c64.CHROUT('\n')
        w1 = max(wa2)
        c64scr.print_word(w1)
        c64.CHROUT('\n')
        f1 = max(fa1)
        c64flt.print_float(f1)
        c64.CHROUT('\n')
        c64.CHROUT('x')
        c64scr.print_ubyte(X)
        c64.CHROUT('\n')

        ub1 = min(uba)
        c64scr.print_ubyte(ub1)
        c64.CHROUT('\n')
        b1 = min(ba)
        c64scr.print_byte(b1)
        c64.CHROUT('\n')
        uw1 = min(uwa)
        c64scr.print_uword(uw1)
        c64.CHROUT('\n')
        w1 = min(wa0)
        c64scr.print_word(w1)
        c64.CHROUT('\n')
        w1 = min(wa1)
        c64scr.print_word(w1)
        c64.CHROUT('\n')
        w1 = min(wa2)
        c64scr.print_word(w1)
        c64.CHROUT('\n')
        f1 = min(fa1)
        c64flt.print_float(f1)
        c64.CHROUT('\n')
        c64.CHROUT('x')
        c64scr.print_ubyte(X)
        c64.CHROUT('\n')

    }
}

