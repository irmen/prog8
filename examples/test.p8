%import c64utils
%zeropage basicsafe

main {

    sub start() {

        str tekst = "the quick brown fox"

        c64scr.print_uw(strlen("aapje"))
        c64.CHROUT('\n')
        c64scr.print_uw(strlen(tekst))
        c64.CHROUT('\n')
    }
}

