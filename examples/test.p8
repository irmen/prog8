%import textio
%zeropage basicsafe


main {
    sub start() {
        repeat {
            uword keys = cx16.kbdbuf_peek2()
            txt.print_uwhex(keys, true)
            if msb(keys) {
                c64.GETIN()
            }
            txt.nl()
        }
    }
}

