%import textio
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        byte @shared b1
        word @shared w1
        long @shared l1

        b1 = -42
        w1 = -4242
        l1 = -42424242

        txt.print_b(sgn(b1))
        txt.spc()
        txt.print_b(sgn(w1))
        txt.spc()
        txt.print_b(sgn(l1))
        txt.spc()

        b1 = 0
        w1 = 0
        l1 = 0

        txt.print_b(sgn(b1))
        txt.spc()
        txt.print_b(sgn(w1))
        txt.spc()
        txt.print_b(sgn(l1))
        txt.spc()

        b1 = 42
        w1 = 4242
        l1 = 42424242

        txt.print_b(sgn(b1))
        txt.spc()
        txt.print_b(sgn(w1))
        txt.spc()
        txt.print_b(sgn(l1))
        txt.spc()
    }
}

