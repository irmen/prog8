%import textio
%zeropage basicsafe

main {
    sub start() {
        @(4000)=100
        txt.print_ubhex(@(4000), false)
        txt.spc()
        @(4000) = -100
        txt.print_ubhex(@(4000), false)
        txt.nl()

        poke(4000, 100)
        txt.print_ubhex(@(4000), false)
        txt.spc()
        poke(4000, -100)
        txt.print_ubhex(@(4000), false)
        txt.nl()

        pokew(4000, 9999)
        txt.print_uwhex(peekw(4000), false)
        txt.spc()
        pokew(4000, -9999)
        txt.print_uwhex(peekw(4000), false)
        txt.nl()

        pokel(4000, 999999)
        txt.print_ulhex(peekl(4000), false)
        txt.spc()
        pokel(4000, -999999)
        txt.print_ulhex(peekl(4000), false)
        txt.nl()
    }
}
