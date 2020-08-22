%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte ub
        uword uw = $ee44
        ubyte vlsb = $11
        ubyte vmsb = $ff

        uw = mkword(vmsb, vlsb)           ; todo flip the order of the operands  , MSB first
        c64scr.print_uwhex(uw, 1)
        uw = mkword($ee, $22)           ; todo flip the order of the operands  , MSB first
        c64scr.print_uwhex(uw, 1)
    }
}

