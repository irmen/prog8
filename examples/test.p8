%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.r0 = memory("buffer", 100, 0)
        cx16.r1 = memory("buffer")

        txt.print_uwhex(cx16.r0, true)
        txt.nl()
        txt.print_uwhex(cx16.r1, true)
        txt.nl()
    }
}
