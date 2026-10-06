%import textio

main {
    uword @shared HORIZ = 60

    sub cellidx(ubyte cx, ubyte cy) -> uword {
        return cx + HORIZ*cy
    }

    sub start() {
        txt.print_uw(cellidx(10, 20))
        txt.nl()
        txt.print_uw(cellidx(11, 20))
        txt.nl()
        txt.print_uw(cellidx(12, 20))
        txt.nl()
    }
}
