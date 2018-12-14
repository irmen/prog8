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

        c64scr.print_byte_decimal(-99)
        c64.CHROUT('\n')
        c64scr.print_byte_decimal(b1)
        c64.CHROUT('\n')
        c64scr.print_word_decimal(-9999)
        c64.CHROUT('\n')
        c64scr.print_word_decimal(w1)
        c64.CHROUT('\n')
    }
}

