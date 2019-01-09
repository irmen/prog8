%import c64utils

~ main {

    sub start()  {

        ;  @todo   '/' with two integer operands should result in integer again instead of having to use '//' all the time?

        ubyte ub = 10
        byte b = -10
        uword uw = 10
        word w = -10

        b = b + (-1)
        b = b - (-1)
        b = 1+b
        b = (-1) + b

        c64scr.print_uw(uw+1)
        c64.CHROUT('\n')
        c64scr.print_uw(uw+2)
        c64.CHROUT('\n')
        c64scr.print_uw(uw+3)
        c64.CHROUT('\n')
        c64scr.print_uw(uw+4)
        c64.CHROUT('\n')

        c64scr.print_uw(uw-1)
        c64.CHROUT('\n')
        c64scr.print_uw(uw-2)
        c64.CHROUT('\n')
        c64scr.print_uw(uw-3)
        c64.CHROUT('\n')
        c64scr.print_uw(uw-4)
        c64.CHROUT('\n')

    }
}
