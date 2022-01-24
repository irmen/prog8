%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        uword m1 = memory("mem1", 123, $100)
        uword m2 = memory("mem2", 999, 2)
        txt.print_uwhex(m1, true)
        txt.nl()
        txt.print_uwhex(m2, true)
        txt.nl()
    }
}
