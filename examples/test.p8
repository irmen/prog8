%import textio
%zeropage basicsafe

main {
    sub func(ubyte bb) {
        bb++
    }

    sub start() {
        func("abc")
    }
}
