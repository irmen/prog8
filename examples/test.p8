%import c64utils

~ main {

    sub start()  {

; @todo more efficient +1/-1 additions in expressions


        ubyte lsbb = $aa
        ubyte msbb = $44
        uword[4] uwarr

        uword uw = (msbb as uword)*256 + lsbb

        c64scr.print_uwhex(0, uw)
        c64.CHROUT('\n')
        uw = mkword(lsbb, msbb)
        c64scr.print_uwhex(0, uw)
        c64.CHROUT('\n')
        uw = mkword($aa, $44)
        c64scr.print_uwhex(0, uw)
        c64.CHROUT('\n')

        uw = mkword(lsbb, $44)
        c64scr.print_uwhex(0, uw)
        c64.CHROUT('\n')
        uw = mkword($aa, msbb)
        c64scr.print_uwhex(0, uw)
        c64.CHROUT('\n')
        uwarr[2] = mkword(lsbb, msbb)
        c64scr.print_uwhex(0, uwarr[2])
        c64.CHROUT('\n')
        uwarr[2] = mkword(lsbb, $44)
        c64scr.print_uwhex(0, uwarr[2])
        c64.CHROUT('\n')
        uwarr[2] = mkword($aa, msbb)
        c64scr.print_uwhex(0, uwarr[2])
        c64.CHROUT('\n')

        word w = mkword(lsbb,msbb) as word
        c64scr.print_w(w)
        c64.CHROUT('\n')

    }
}
