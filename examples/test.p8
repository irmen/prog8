%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte value = 1
        uword wvalue = 1
        ubyte zero = 0
        txt.print_ub(value<<zero)       ; TODO fix result 6502 codegen! should be 1.
        txt.print_uw(wvalue<<zero)      ; TODO fix result 6502 codegen! should be 1.
        ubyte value2 = value<<zero      ; result is ok, 1
        uword wvalue2 = wvalue<<zero    ; result is ok, 1
        txt.print_ub(value2)
        txt.print_uw(wvalue2)
    }
}
