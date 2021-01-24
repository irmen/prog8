%import textio
%zeropage basicsafe

main {


    sub start() {
        uword xx
        uword iter = 256
        repeat iter {
            xx++
        }

        txt.print_uw(xx)
    }
}
