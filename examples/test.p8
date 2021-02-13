%import textio
%zeropage basicsafe

main {

    sub start() {

        uword ptr = $4000

        @(ptr) = $34
        @(ptr+1) = $ea

        txt.print_ubhex(@(ptr), 1)
        txt.print_ubhex(@(ptr+1), 1)
        txt.nl()

        uword ww = mkword(@(ptr+1), @(ptr))         ; TODO FIX
        txt.print_uwhex(ww,1)
        txt.nl()

        ubyte low = @(ptr)
        ubyte high = @(ptr+1)
        ww = mkword(high, low)
        txt.print_uwhex(ww,1)
        txt.nl()
    }
}
