%zeropage basicsafe

main {
    sub start() {
        const uword buffer = 2222

        cx16.r0 = &buffer           ; TODO: should give an error that you cannot take the address of a constant
    }
}
