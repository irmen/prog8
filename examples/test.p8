%zeropage basicsafe

main {

    sub start() {
        word[4] birdX
        byte testbyte = 0
        ubyte j = 0
        repeat {
            birdX[j] += testbyte
        }
    }
}
