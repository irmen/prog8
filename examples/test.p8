%import textio
%zeropage basicsafe

main {
    sub start() {
        byte bb1 = -50
        byte bb2 = -51

        word ww = func(bb1, bb2)
        txt.print_w(ww)
        txt.print(" <- must be -50\n")          ; TODO fix this with noopt (prints 0) !

        ubyte ub1 = 50
        ubyte ub2 = 51
        uword uw = funcu(ub1, ub2)
        txt.print_uw(uw)
        txt.print(" <- must be 50\n")            ; TODO fix this with noopt (prints 0) !
    }

    sub func(word x1, word y1) -> word {
        return x1
    }

    sub funcu(uword x1, uword y1) -> uword {
        return x1
    }
}
