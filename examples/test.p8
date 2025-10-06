%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv1 = 12345678

        txt.print_l(lv1)
        txt.spc()
        txt.print_uw(&lv1)
        txt.spc()
        txt.print_l(peekl(&lv1))
        txt.nl()

        pokel(&lv1, -6666666)
        sys.pushl(lv1)

        long lv2 = sys.popl()
        txt.print_l(lv2)
    }
}
