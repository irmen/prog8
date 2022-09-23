%import textio
%zeropage basicsafe

main {

    sub start() {

        uword[] words = [1111,2222,0,4444,3333]

        txt.print_ub(all(words))
        txt.nl()
        txt.print_ub(any(words))
        txt.nl()
        sort(words)

        uword ww
        for ww in words {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.nl()

        reverse(words)
        for ww in words {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.nl()

    }
}

