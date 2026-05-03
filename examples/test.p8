%import textio
%zeropage basicsafe

main {
    sub start() {
        byte @shared b1 = 99
        byte @shared b2 = -33
        word @shared w1 = 9999
        word @shared w2 = -3333
        long @shared l1 = 999999
        long @shared l2 = -333333

        txt.print_b(min(b1, b2))
        txt.nl()
        txt.print_b(max(b1, b2))
        txt.nl()
        txt.print_b(clamp(b1, 10, 88))
        txt.spc()
        txt.print_b(clamp(b2, -99, -44))
        txt.spc()
        txt.nl()
        txt.nl()

        txt.print_w(min(w1, w2))
        txt.nl()
        txt.print_w(max(w1, w2))
        txt.nl()
        txt.print_w(clamp(w1, 1000, 8888))
        txt.spc()
        txt.print_w(clamp(w2, -9999, -4444))
        txt.nl()
        txt.nl()

        txt.print_l(min(l1, l2))
        txt.nl()
        txt.print_l(max(l1, l2))
        txt.nl()
        txt.print_l(clamp(l1, 1000000, 888888))
        txt.spc()
        txt.print_l(clamp(l2, -999999, -444444))
        txt.nl()
        txt.nl()

        txt.print_b(sgn(b2))
        txt.nl()
        txt.print_b(sgn(w2))
        txt.nl()
        txt.print_b(sgn(l2))
        txt.nl()
        txt.nl()

        txt.print_ub(abs(b2))
        txt.nl()
        txt.print_uw(abs(w2))
        txt.nl()
        txt.print_l(abs(l2))
        txt.nl()
    }
}
