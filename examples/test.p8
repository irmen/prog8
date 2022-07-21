%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        word bb = -15
        bb /= 4
        txt.print_w(bb)
        txt.nl()
        bb = 15
        bb /= 4
        txt.print_w(bb)
        txt.nl()
        uword ubb = 15
        ubb /= 4
        txt.print_uw(ubb)
        txt.nl()

        recurse1()
    }

    sub recurse1() {
        recurse2()
    }

    sub recurse2() {
        start()
    }
}
