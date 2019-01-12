%import c64utils

~ main {

    sub start()  {

        byte b
        ubyte ub
        memory ubyte mb = $c000
        memory uword muw = $c000
        word w
        uword uw
        uword[4] uwa

        ub=%10001011
        for ubyte i in 0 to 10 {
            c64scr.print_ubbin(1, ub)
            rol2(ub)
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')

        uw=%1000101100001110
        for ubyte i in 0 to 10 {
            c64scr.print_uwbin(1, uw)
            rol2(uw)
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')

        muw=%1000101100001110
        for ubyte i in 0 to 10 {
            c64scr.print_uwbin(1, muw)
            rol2(muw)
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')

        ubyte x=2
        uwa[x]=%1000101100001110
        for ubyte i in 0 to 10 {
            c64scr.print_uwbin(1, uwa[x])
            rol2(uwa[x])
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')

        uwa[2]=%1000101100001110
        for ubyte i in 0 to 10 {
            c64scr.print_uwbin(1, uwa[2])
            rol2(uwa[2])        ; @todo wrong
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')
    }
}
