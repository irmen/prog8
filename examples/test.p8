%import textio
%import syslib
%zeropage basicsafe
main {
    sub start() {
        ubyte xx = 1
        uword ww=1


        xx |= %0001000
        txt.print_ubbin(xx, true)
        txt.nl()
        xx &= %11110111
        txt.print_ubbin(xx, true)
        txt.nl()


        ww |= %0001000
        txt.print_uwbin(ww, true)
        txt.nl()
        ww &= %11110111
        txt.print_uwbin(ww, true)
        txt.nl()
    }
}
