%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        word  ww = calculate(6)
        txt.print_w(ww)
        txt.nl()
        ubyte bb = calculate2(6)
        txt.print_ub(bb)
        txt.nl()
    }

    sub calculate2(ubyte row) -> ubyte {
        return 8+row
    }


    sub calculate(ubyte row) -> word {
        return 8+row
    }
}
