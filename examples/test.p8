%import textio
%zeropage basicsafe

main {

    sub start() {
        uword scrpos = $0400
        repeat 256 {
            @(scrpos) = 81
            scrpos++
        }

        txt.print_uw(scrpos)
        txt.nl()

    }
}
