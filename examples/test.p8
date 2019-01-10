%import c64utils

~ main {

    sub start()  {

        ubyte i
        byte j
        uword uw
        word w

        for i in 5 to 0 step -1 {
            c64scr.print_ub(i)
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')

        for j in 5 to 0 step -1 {
            c64scr.print_b(j)
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')

        for j in -5 to 0 {
            c64scr.print_b(j)
            c64.CHROUT('\n')
        }
        c64.CHROUT('\n')
    }
}
