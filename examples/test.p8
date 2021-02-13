%import textio
%zeropage basicsafe

main {

    sub start() {

        uword ptr = $4000

        poke(ptr, $34)
        poke(ptr+1, $ea)
        txt.print_ubhex(peek(ptr), 1)
        txt.print_ubhex(peek(ptr+1), 1)
        txt.nl()

        uword ww = peekw(ptr)
        txt.print_uwhex(ww,1)
        txt.nl()

        pokew(ptr, $98cf)
        ww = peekw(ptr)
        txt.print_uwhex(ww,1)
        txt.nl()
    }
}
