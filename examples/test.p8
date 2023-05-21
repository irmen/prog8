%import textio
%zeropage basicsafe

main {

    sub start() {

        uword table = memory("table", 100, 0)

        ubyte pos
        for pos in 0 to 7 {
            pokew(table + 64 + pos*2, ($000a-pos)*200)
        }

        for pos in 0 to 7 {
            txt.print_uw(peekw(table+64+pos*2))
            txt.nl()
        }
    }
}

