%import textio
%zeropage basicsafe

main {
    const ubyte VAL = 11
    sub start() {
        uword w

        for w in 0 to 20 {
            ubyte x,y,z=13

            txt.print_ub(x)
            txt.spc()
            txt.print_ub(y)
            txt.spc()
            txt.print_ub(z)
            txt.spc()
            txt.print_uw(w)
            txt.nl()
            x++
            y++
            z++
        }
    }
}
