%zeropage basicsafe

main {
    ubyte @requirezp foobar2 = 255
    sub start() {
        ubyte @requirezp foobar = 255
        foobar++
        foobar2++
    }
}
