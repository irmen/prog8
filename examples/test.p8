%import buffers
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        signed()
        unsigned()
    }

    sub signed() {
        txt.print("signed\n")
        byte @shared bvalue = -88
        word @shared wvalue = -8888

        txt.print_b(bvalue/2)
        txt.nl()
        txt.print_w(wvalue/2)
        txt.nl()

        bvalue /= 2
        wvalue /= 2

        txt.print_b(bvalue)
        txt.nl()
        txt.print_w(wvalue)
        txt.nl()

        bvalue *= 2
        wvalue *= 2

        txt.print_b(bvalue)
        txt.nl()
        txt.print_w(wvalue)
        txt.nl()
        txt.nl()

        txt.print_b(bvalue/4)
        txt.nl()
        txt.print_w(wvalue/4)
        txt.nl()

        bvalue /= 4
        wvalue /= 4

        txt.print_b(bvalue)
        txt.nl()
        txt.print_w(wvalue)
        txt.nl()

        bvalue *= 4
        wvalue *= 4

        txt.print_b(bvalue)
        txt.nl()
        txt.print_w(wvalue)
        txt.nl()
        txt.nl()
    }

    sub unsigned() {
        txt.print("unsigned\n")
        ubyte @shared ubvalue = 88
        uword @shared uwvalue = 8888

        txt.print_ub(ubvalue/2)
        txt.nl()
        txt.print_uw(uwvalue/2)
        txt.nl()

        ubvalue /= 2
        uwvalue /= 2

        txt.print_ub(ubvalue)
        txt.nl()
        txt.print_uw(uwvalue)
        txt.nl()

        ubvalue *= 2
        uwvalue *= 2

        txt.print_ub(ubvalue)
        txt.nl()
        txt.print_uw(uwvalue)
        txt.nl()
        txt.nl()

        txt.print_ub(ubvalue/4)
        txt.nl()
        txt.print_uw(uwvalue/4)
        txt.nl()

        ubvalue /= 4
        uwvalue /= 4

        txt.print_ub(ubvalue)
        txt.nl()
        txt.print_uw(uwvalue)
        txt.nl()

        ubvalue *= 4
        uwvalue *= 4

        txt.print_ub(ubvalue)
        txt.nl()
        txt.print_uw(uwvalue)
        txt.nl()
    }
}
