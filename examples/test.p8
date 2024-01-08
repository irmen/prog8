%import textio
%zeropage basicsafe
%option no_sysinit



main {
    sub start() {
        ubyte @shared color = 255
        uword @shared ww = 12345
        plot(color)
        txt.nl()
        plotw(ww)
        txt.nl()
        color = ww = 0
        plot(color)
        txt.nl()
        plotw(ww)
        txt.nl()
    }

    sub plot(bool draw) {
        txt.print_ub(draw)
    }

    sub plotw(bool draw) {
        txt.print_ub(draw)
    }

}
