%import floats
%import textio

%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        pokebool(3000, true)
        pokew(3100, 12345)
        pokef(3200, 3.1415927)
        poke(3300, 222)

        txt.print_bool(peekbool(3000))
        txt.nl()
        txt.print_uw(peekw(3100))
        txt.nl()
        txt.print_f(peekf(3200))
        txt.nl()
        txt.print_ub(peek(3300))
        txt.nl()
    }
}
