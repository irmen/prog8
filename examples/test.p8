%import textio
%zeropage basicsafe


main {
    sub start() {
        word w1, w2, w3, w4
        uword uw1, uw2, uw3

        w1 = -111
        w2 = 222
        w3 = -333
        w4 = -20

        uw1 = 111
        uw2 = 222
        uw3 = 333

        txt.print_w(w2*w3)
        txt.spc()
        w1 = w2 * w3
        txt.print_w(w1)
        txt.nl()
        txt.print_w(w3*w4)
        txt.nl()

        txt.print_uw(uw2*uw3)
        txt.spc()
        uw1 = uw2 * uw3
        txt.print_uw(uw1)
        txt.nl()
        txt.nl()


        w1 = -111
        w2 = 22222
        w3 = -333
        w4 = -17

        uw1 = 111
        uw2 = 22222
        uw3 = 333

        txt.print_w(w2/w3)
        txt.spc()
        w1 = w2 / w3
        txt.print_w(w1)
        txt.nl()
        txt.print_w(w3/w4)
        txt.nl()

        txt.print_uw(uw2/uw3)
        txt.spc()
        uw1 = uw2 / uw3
        txt.print_uw(uw1)
        txt.nl()
    }
}
