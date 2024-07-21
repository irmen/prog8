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
        txt.spc()
        txt.print_b(bvalue/4)
        txt.spc()
        txt.print_b(bvalue/8)
        txt.nl()

        bvalue /= 2
        txt.print_b(bvalue)
        txt.spc()
        bvalue /= 8
        txt.print_b(bvalue)
        txt.nl()

        txt.print_w(wvalue/2)
        txt.spc()
        txt.print_w(wvalue/4)
        txt.spc()
        txt.print_w(wvalue/8)
        txt.nl()

        wvalue /= 2
        txt.print_w(wvalue)
        txt.spc()
        wvalue /= 8
        txt.print_w(wvalue)
        txt.nl()
    }

    sub unsigned() {
        txt.print("\nunsigned\n")
        ubyte @shared bvalue = 88
        uword @shared wvalue = 8888

        txt.print_ub(bvalue/2)
        txt.spc()
        txt.print_ub(bvalue/4)
        txt.spc()
        txt.print_ub(bvalue/8)
        txt.nl()

        bvalue /= 2
        txt.print_ub(bvalue)
        txt.spc()
        bvalue /= 8
        txt.print_ub(bvalue)
        txt.nl()

        txt.print_uw(wvalue/2)
        txt.spc()
        txt.print_uw(wvalue/4)
        txt.spc()
        txt.print_uw(wvalue/8)
        txt.nl()

        wvalue /= 2
        txt.print_uw(wvalue)
        txt.spc()
        wvalue /= 8
        txt.print_uw(wvalue)
        txt.nl()
    }
}
