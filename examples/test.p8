%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

main {

    sub start() {
        byte ub = 100
        byte ub2
        word uw = 22222
        word uw2

        ub = -100
        c64scr.print_b(ub >> 1)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 2)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 7)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 8)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 9)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 16)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 26)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        ub = 100
        c64scr.print_b(ub >> 1)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 2)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 7)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 8)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 9)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 16)
        c64.CHROUT('\n')
        c64scr.print_b(ub >> 26)
        c64.CHROUT('\n')
        c64.CHROUT('\n')


        uw = -22222
        c64scr.print_w(uw >> 1)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 7)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 8)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 9)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 15)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 16)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 26)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        uw = 22222
        c64scr.print_w(uw >> 1)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 7)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 8)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 9)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 15)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 16)
        c64.CHROUT('\n')
        c64scr.print_w(uw >> 26)
        c64.CHROUT('\n')

    }
}
