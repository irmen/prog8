%import textio
%zeropage basicsafe

main {

    sub start() {

        uword ptr = $4000

        @(ptr) = $34
        @(ptr+1) = $ea

        txt.print_ubhex(peek(ptr), 1)
        txt.print_ubhex(peek(ptr+1), 1)
        txt.nl()

        uword ww = peekw(ptr)
        txt.print_uwhex(ww,1)
        txt.nl()

        ubyte low = peek(ptr)
        ubyte high = peek(ptr+1)
        ww = mkword(high, low)
        txt.print_uwhex(ww,1)
        txt.nl()
    }
}
