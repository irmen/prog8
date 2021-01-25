%import textio
%zeropage basicsafe

main {


    sub start() {
        uword xx
        uword iter = 1000
        repeat iter {
            xx++
        }

        txt.print_uw(xx)
    }
}
