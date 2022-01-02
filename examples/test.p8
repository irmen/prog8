%import textio
%zeropage basicsafe

main {
    uword buffer = memory("the quick brown fox jumps over the lazy dog", 2000)
    uword buffer2 = memory("the quick brown fox jumps over the lazy dog", 2000)

    sub start() {
        txt.print_uwhex(buffer, true)
        txt.print_uwhex(buffer2, true)
    }

}
