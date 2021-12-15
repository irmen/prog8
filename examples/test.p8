%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        singleparamb(123)
        singleparamw(-9999)
        doubleparamb(123,-99)
        doubleparamw(8888,-9999)
        singleparamf(1.23456)
    }

    sub singleparamb(ubyte bb) {
        txt.print_ub(bb)
        txt.nl()
    }

    sub doubleparamb(ubyte bb, byte bs) {
        txt.print_ub(bb)
        txt.spc()
        txt.print_b(bs)
        txt.nl()
    }

    sub singleparamw(word ww) {
        txt.print_w(ww)
        txt.nl()
    }

    sub doubleparamw(uword uw, word ww) {
        txt.print_uw(uw)
        txt.spc()
        txt.print_w(ww)
        txt.nl()
    }

    sub singleparamf(float ff) {
        floats.print_f(ff)
        txt.nl()
    }
}
