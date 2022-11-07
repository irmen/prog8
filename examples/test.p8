%import textio
%zeropage basicsafe

main {
    ubyte @shared qqq=123

    &uword mapped = $ea31

    sub start() {
        ubyte bb = 99
        txt.print_ub(bb)
        txt.print("Hello, world!")
        uword ww = bb
        txt.print_uw(bb)
        txt.print_uw(ww)
    }
}
