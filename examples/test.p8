%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[] barr = [1,2,3,4,5,6,7,8,9]
        uword[] warr = [111,222,333,444,555,666,777,888,999]
        uword pointer = &barr
        byte index = 2

        txt.print_ub(barr[7])
        txt.nl()
        txt.print_ub(barr[-2])
        txt.nl()
        txt.print_ub(pointer[7])
        txt.nl()
        txt.print_ub(pointer[index])
        txt.nl()
        txt.print_uw(warr[7])
        txt.nl()
        txt.print_uw(warr[-2])
        txt.nl()
    }
}
