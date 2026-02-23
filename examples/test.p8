%import textio
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        ubyte @shared b1, b2, b3, b4
        b1 =  250
        b2 =  29
        b3, b4 = divmod(b1, b2)
        txt.print_ub(b3)
        txt.spc()
        txt.print_ub(b4)
        txt.nl()

        uword @shared u1, u2, u3, u4
        u1 = 40000
        u2 = 165
        u3, u4 = divmod(u1, u2)
        txt.print_uw(u3)
        txt.spc()
        txt.print_uw(u4)
        txt.nl()
    }
}

