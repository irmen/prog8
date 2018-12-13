%import c64utils
%option enable_floats

~ main {

    sub start()  {

        ubyte ub1
        byte b1
        uword uw1
        word  w1
        float f1
        float f2
        float f3


        f1=22.555
        f2=15.123
        f3 = f1+f2
        c64flt.print_float(f3)
        c64.CHROUT('\n')

        f1=22.555
        f2=15.123
        f3 = f1-f2
        c64flt.print_float(f3)
        c64.CHROUT('\n')

        f1=22.555
        f2=15.123
        f3 = f1*f2
        c64flt.print_float(f3)
        c64.CHROUT('\n')

        f1=22.555
        f2=15.123
        f3 = f1/f2
        c64flt.print_float(f3)
        c64.CHROUT('\n')

        f3 = -f1
        c64flt.print_float(f3)
        c64.CHROUT('\n')

        f3++
        c64flt.print_float(f3)
        c64.CHROUT('\n')
        f3++
        c64flt.print_float(f3)
        c64.CHROUT('\n')
        f3++
        c64flt.print_float(f3)
        c64.CHROUT('\n')
        f3--
        c64flt.print_float(f3)
        c64.CHROUT('\n')
        f3--
        c64flt.print_float(f3)
        c64.CHROUT('\n')
        f3--
        c64flt.print_float(f3)
        c64.CHROUT('\n')
    }
}

