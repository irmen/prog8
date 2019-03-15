%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    sub start() {

        ubyte ub
        A = 123 or 44
        Y = A or 1
        ub = ub or 1
    }
}
