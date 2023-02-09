%zeropage basicsafe

main {

    &ubyte[40] topline = $0400

    sub start() {
        topline[0] = 'a'
        topline[1] = 'b'
        topline[2] = 'd'
        topline[39] = '@'
    }
}
