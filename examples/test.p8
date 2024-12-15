%import textio
%zeropage basicsafe

main {
    sub start() {
        uword[] addresses = [scores2, start]
        uword[] scores1 = [10, 25, 50, 100]
        uword[] scores2 = [100, 250, 500, 1000]

        cx16.r0 = &scores1
        cx16.r1 = &scores2
        cx16.r2 = &addresses
    }
}
