%zeropage basicsafe

main {
    sub start() {
        repeat cx16.r0 {
            cx16.r1L++
        }
    }
}
