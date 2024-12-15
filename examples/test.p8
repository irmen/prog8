%import textio
%zeropage basicsafe

main {
    uword large = memory("large", 20000, 256)

    sub start() {
        for cx16.r1 in large to large+20000-1 {
            cx16.r0++
        }
    }
}
