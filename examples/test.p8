%import conv
%import textio
%zeropage basicsafe

main {
    sub start() {
        uword value
        ubyte size

        value, size = conv.any2uword("123")
        txt.print_uw(value)
        txt.spc()
        txt.print_ub(size)
        txt.nl()

        value, size = conv.any2uword("$ea31")
        txt.print_uw(value)
        txt.spc()
        txt.print_ub(size)
        txt.nl()

        value, size = conv.any2uword("%11111110")
        txt.print_uw(value)
        txt.spc()
        txt.print_ub(size)
        txt.nl()
    }
}
