%import textio
%zeropage basicsafe

main {
    sub start() {
        uword address = 1000

        poke(1000, 99)
        ubyte prev = pokemon(1000,123)
        txt.print_ub(prev)
        txt.nl()
        prev = pokemon(1000,0)
        txt.print_ub(prev)
        txt.nl()
        txt.print_ub(@(1000))
        txt.nl()
        txt.nl()

        poke(address+3, 99)
        prev = pokemon(address+3,123)
        txt.print_ub(prev)
        txt.nl()
        prev = pokemon(address+3,0)
        txt.print_ub(prev)
        txt.nl()
        txt.print_ub(@(address+3))
        txt.nl()
    }
}
