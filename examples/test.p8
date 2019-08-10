%import c64lib
%import c64utils

main {

    sub start() {

        for ubyte ax in 0 to 255 {
            word wcosa = cos8(ax) as word
            word wsina = sin8(ax) as word

            c64scr.print_ub(ax)
            c64.CHROUT(':')
            c64.CHROUT(' ')
            c64scr.print_w(wcosa)
            c64.CHROUT(',')
            c64scr.print_w(wsina)
            c64.CHROUT('\n')
        }
    }
}
