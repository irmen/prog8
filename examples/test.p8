%import textio
%import string
%zeropage basicsafe

main {

    sub start() {
        uword seconds_uword = 1
        uword remainder = seconds_uword % $0003 ==0
        txt.print_uw(remainder)
    }
}

