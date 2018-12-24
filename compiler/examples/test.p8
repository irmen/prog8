%import c64utils
;%option enable_floats

~ main {

    ;@todo implement the various byte/word division routines.

     ;c64scr.PLOT(screenx(x), screeny(y))    ; @todo fix argument calculation of parameters ???!!!

    sub screenx(float x) -> word {
        ;return ((x/4.1* (width as float)) + 160.0) as word ;width // 2       ; @todo fix calculation
        float wf = width
        return (x/4.1* wf + wf / 2.0)   as word
    }

    sub start()  {

        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        word w = c64utils.str2word("000")
        c64scr.print_w(w)
        c64.CHROUT('\n')
        w = c64utils.str2word("1")
        c64scr.print_w(w)
        c64.CHROUT('\n')
        w = c64utils.str2word("-15000")
        c64scr.print_w(w)
        c64.CHROUT('\n')
        w = c64utils.str2word("15000")
        c64scr.print_w(w)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        uword uw = c64utils.str2uword("0")
        c64scr.print_uw(uw)
        c64.CHROUT('\n')
        uw = c64utils.str2uword("1")
        c64scr.print_uw(uw)
        c64.CHROUT('\n')
        uw = c64utils.str2uword("15000")
        c64scr.print_uw(uw)
        c64.CHROUT('\n')
        uw = c64utils.str2uword("65522")
        c64scr.print_uw(uw)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        byte b = c64utils.str2byte("0")
        c64scr.print_b(b)
        c64.CHROUT('\n')
        b=c64utils.str2byte("10")
        c64scr.print_b(b)
        c64.CHROUT('\n')
        b=c64utils.str2byte("-10")
        c64scr.print_b(b)
        c64.CHROUT('\n')
        b=c64utils.str2byte("-128")
        c64scr.print_b(b)
        c64.CHROUT('\n')
        b=c64utils.str2byte("127")
        c64scr.print_b(b)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        ubyte ub = c64utils.str2ubyte("0")
        c64scr.print_ub(ub)
        c64.CHROUT('\n')
        ub=c64utils.str2ubyte("10")
        c64scr.print_ub(ub)
        c64.CHROUT('\n')
        ub=c64utils.str2ubyte("10")
        c64scr.print_ub(ub)
        c64.CHROUT('\n')
        ub=c64utils.str2ubyte("128")
        c64scr.print_ub(ub)
        c64.CHROUT('\n')
        ub=c64utils.str2ubyte("255")
        c64scr.print_ub(ub)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

    }
}

