%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        word ww = -1234
        float fl = 123.34
        byte bb = -99

        txt.print_w(abs(ww))
        txt.nl()
        txt.print_b(abs(bb))
        txt.nl()
        floats.print_f(abs(fl))
        txt.nl()
        floats.print_f(sqrt(fl))
        txt.nl()
    }
}

