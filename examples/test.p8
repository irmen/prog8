%import textio
%zeropage basicsafe

main {
    sub start() {
        ; TODO test clamp long, min long, max long

        long @shared l1 = 999999
        long @shared l2 = -333333

        txt.print_l(min(l1, l2))
        txt.nl()
        txt.print_l(max(l1, l2))
        txt.nl()
        txt.print_l(abs(l2))
        txt.nl()
        txt.print_b(sgn(l2))
        txt.nl()
        txt.print_l(clamp(l1, 20000, 88888))
        txt.nl()
    }
}
