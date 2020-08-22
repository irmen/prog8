%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte ub
        uword uw
        word w
        byte b
        float f


        ub = 4 |  ub | 2
        ub = ub | 2 | 7
        ub = 4 | 2 | ub

        ub = 4 + ub + 2
        ub = ub + 2 + 7
        ub = 4 + 2 + ub
    }
}

