%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] array
        ubyte idx = 20
        idx += 10
        ubyte amount = 20
        array[idx] -= 10
        array[idx] -= amount
    }
}
