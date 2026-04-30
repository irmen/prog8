%zeropage basicsafe

main {

    sub start() {
        uword @shared wvar = 5

        if wvar <= 10
            cx16.r0++

        if wvar > 10
            cx16.r1++
    }
}
