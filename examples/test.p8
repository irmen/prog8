%import textio

main {
    sub start() {
        uword width
        uword width2 = 12345
        ubyte ub1
        ubyte ub2 = 123

        ub1 = ub2 % 32
        txt.print_ub(ub1)
        txt.nl()
        width = width2 % 32
        txt.print_uw(width)

    }
}

