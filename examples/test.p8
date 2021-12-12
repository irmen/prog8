%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared yy

        if yy&64 {
            yy++
        }
    }
}
