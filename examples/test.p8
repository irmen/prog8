%import textio
%zeropage dontuse

main {
    sub start() {
        uword empty
        uword block = memory("block", 500, 0)

        txt.print_uwhex(&empty, true)
        txt.nl()
        txt.print_uwhex(block, true)
        txt.nl()
    }
}
