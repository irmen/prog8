%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8
    ; @todo compiler error for using literal values other than 0 or 1 with boolean expressions

    sub start() {

        c64.CLEARSCR()      ; @todo empty stack exception in vm
    }
}
