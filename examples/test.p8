%import textio
%zeropage basicsafe

main {
    const ubyte VAL = 11
    sub start() {
        uword w
        ubyte x
        ubyte y
        ubyte z

        w = x = y = z = 99+VAL
        txt.print_ub(x)
        txt.spc()
        txt.print_ub(y)
        txt.spc()
        txt.print_ub(z)
        txt.spc()
        txt.print_uw(w)
        txt.nl()
    }
}
