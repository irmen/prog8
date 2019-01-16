%import c64utils
%import c64flt

~ main {

    sub start()  {

        ubyte ub1
        ubyte ub2
        ubyte ub3
        ubyte ub4
        byte b1
        byte b2
        byte b3
        byte b4
        word w1
        word w2
        word w3
        word w4
        uword uw1
        uword uw2
        uword uw3
        uword uw4
        float f1
        float f2
        float f3
        float f4
        memory ubyte mub1 = $c000
        memory ubyte mub2 = $c000
        memory ubyte mub3 = $c000
        memory ubyte mub4 = $c000
        memory byte mb1 = $c000
        memory byte mb2 = $c000
        memory byte mb3 = $c000
        memory byte mb4 = $c000
        memory word mw1 = $c000
        memory word mw2 = $c000
        memory word mw3 = $c000
        memory word mw4 = $c000
        memory uword muw1 = $c000
        memory uword muw2 = $c000
        memory uword muw3 = $c000
        memory uword muw4 = $c000
        memory float mf1 = $c010
        memory float mf2 = $c020
        memory float mf3 = $c030
        memory float mf4 = $c040

        ub1 = $11
        ub2 = $11
        ub3 = $11
        mub1 = $11
        mub2 = $11
        mub3 = $11
        ub4 = $44
        mub4 = $44

        b1=$11
        b2=$11
        b3=$11
        mb1=$11
        mb2=$11
        mb3=$11
        b4=$44
        mb4=$44

        w1=$1111
        w2=$1111
        w3=$1111
        mw1=$1111
        mw2=$1111
        mw3=$1111
        w4=$4444
        mw4=$4444

        uw1=$1111
        uw2=$1111
        uw3=$1111
        muw1=$1111
        muw2=$1111
        muw3=$1111
        uw4=$4444
        muw4=$4444

        f1 = 12.11
        f1 = 13.11
        f1 = 14.11
        f1 = 15.11
        f1 = 11.11
        f2 = 11.11
        f3 = 11.11
        mf1 = 11.11
        mf2 = 11.11
        mf3 = 11.11
        f4 = 44.44
        mf4 = 44.44

        c64flt.print_f(f1)
        c64.CHROUT('\n')
        c64flt.print_f(f2)
        c64.CHROUT('\n')
        c64flt.print_f(f3)
        c64.CHROUT('\n')
        c64flt.print_f(f4)
        c64.CHROUT('\n')
        c64flt.print_f(mf1)
        c64.CHROUT('\n')
        c64flt.print_f(mf2)
        c64.CHROUT('\n')
        c64flt.print_f(mf3)
        c64.CHROUT('\n')
        c64flt.print_f(mf4)
        c64.CHROUT('\n')


    }

}

