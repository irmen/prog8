%import c64utils
%zeropage basicsafe

main {

    sub start() {
        uword addr=$d020
        ubyte q =2
        @(addr) += q
    }
}

