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


        b2 = 99
        w2 = -9999
        ub2 = 100
        uw2 = 40000
        f2 = 3.141592654

        c64.CHROUT('x')
        c64scr.print_ubyte_decimal(X)
        c64.CHROUT('\n')
        f1 = deg(f2)
        c64flt.print_float(f1)
        c64.CHROUT('\n')
        c64.CHROUT('x')
        c64scr.print_ubyte_decimal(X)
        c64.CHROUT('\n')
        f1 = rad(f1)
        c64flt.print_float(f1)
        c64.CHROUT('\n')
        c64.CHROUT('x')
        c64scr.print_ubyte_decimal(X)
        c64.CHROUT('\n')

    }
}

