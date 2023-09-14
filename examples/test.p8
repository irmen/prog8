%import textio
%zeropage basicsafe

main {
    sub start() {
        uword zz = 0

        @(&zz) = 1
        txt.print_uw(zz)
        txt.nl()
        @(&zz+1) = 2
        txt.print_uw(zz)
        txt.nl()
        ubyte bb
        bb = @(&zz)
        txt.print_ub(bb)
        txt.nl()
        bb = @(&zz+1)
        txt.print_ub(bb)
        txt.nl()
    }
}
