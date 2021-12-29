%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte xx
        ubyte yy

        if xx==0 or xx==4 {
            xx++
        }
        txt.nl()
    }
}
