%import textio
%import math
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte lower = 123
        ubyte upper = 0
        uword ww = mkword(upper, lower)

        txt.print_uwhex(ww, true)
    }
}
