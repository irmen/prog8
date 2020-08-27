%import c64textio
%zeropage basicsafe

main {
    sub start() {
        ubyte b1 = 2
        ubyte b2 = 13
        ubyte b3 = 100

        uword w1 = 2222
        uword w2 = 11
        uword w3 = 33

        w1 %= (w2+w3)
        txt.print_uw(w1)
        c64.CHROUT('\n')
    }
}
