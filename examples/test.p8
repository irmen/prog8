%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.r0 = memory("buffer", 100, 0)
        cx16.r1 = memory("buffer", 100, 0)
        cx16.r2 = memory("buffer", 100, 2)
        ;cx16.r3 = memory("buffer")
    }
}
