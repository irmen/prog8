%zeropage basicsafe

main {
    sub start() {
        ubyte @requirezp b1
        ubyte @zp b2
        ubyte b3
        ubyte @nozp b4

        b1++
        b2++
        b3++
        b4++
    }
}
