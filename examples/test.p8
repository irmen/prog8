%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte x8
        ubyte x4
        ubyte x3
        ubyte result = x3 % x8
        txt.print_ub(result)
        txt.nl()
        result = x3/x8
        txt.print_ub(result)
        txt.nl()

        uword y8
        uword y4
        uword y3
        uword wresult = y3 % y8
        txt.print_uw(wresult)
        txt.nl()
        wresult = y3 / y8
        txt.print_uw(wresult)
        txt.nl()
    }
}

