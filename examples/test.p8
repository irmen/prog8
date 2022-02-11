%zeropage basicsafe

main {
    sub start() {
        ubyte @shared xx

        if xx==1 or xx==2 or xx==3 {
            xx++
        }
    }
}
