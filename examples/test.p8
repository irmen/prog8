%zeropage basicsafe

main {
    uword w1
    uword w2

    sub start() {
        bool zz = (w1 & w2)==0

        if (w1 & w2)
            w1++

        if not(w1 & w2)
            w1++
    }
}
