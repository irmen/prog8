%import c64utils
%zeropage basicsafe

main {

    sub start() {
        ubyte bb = 1
        uword addr=$d020
        @(addr) = bb
        bb = @(addr)
    }
}

