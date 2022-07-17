%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte ci
        ubyte from=10
        ubyte end=1

        for ci in from to end {
            txt.print_ub(ci)
            txt.spc()
        }
        txt.nl()
    }
}
