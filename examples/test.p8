%import textio
%zeropage basicsafe

main {
    sub start() {
        thing()
    }
    sub thing() {
        ubyte start=22
        txt.print_ub(start)
        txt.print_uw(&main.start)
    }
}
