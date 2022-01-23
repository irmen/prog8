%import textio
%zeropage basicsafe

main {
    sub start() {
        word ww
        ww = calculate(6)
        txt.print_w(ww)
        txt.nl()
        ww = calculate(8)
        txt.print_w(ww)
        txt.nl()
        ww = calculate(10)
        txt.print_w(ww)
        txt.nl()
    }

    sub calculate(ubyte row) -> word {
        return 8-(row as byte)
    }
}
