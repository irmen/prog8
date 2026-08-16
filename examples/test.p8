%zeropage basicsafe
%import textio

main {
    sub start() {
        long @shared z1 = "sadfasdf1"
        pointer @shared z2 = "sadfasdf2"

        txt.print_ulhex(z1, true)
        txt.nl()
        txt.print_ulhex(z2, true)
        txt.nl()
    }
}
