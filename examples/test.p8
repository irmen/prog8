%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        poke($4000,123)
        txt.print_ub(peek($4000))
        txt.nl()
        pokew($4002, 55555)
        txt.print_uw(peekw($4002))
        txt.nl()
        float value=123.45678
        pokef($4004, value)
        floats.print_f(peekf($4004))
        txt.nl()
    }
}
