%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared xx
        ubyte @shared yy

        yy = xx==7
        if xx==99 {
            xx++
            yy++
        }
        txt.nl()
    }
}
