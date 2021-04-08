%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte lives=2
        ubyte lvs

        for lvs in 10 to lives {
            txt.print_ub(lvs)
            txt.spc()
        }
        txt.nl()
    }
}

