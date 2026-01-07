%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv

        lv = 12345678
        lv >>= 8
        txt.print_l(lv)
        txt.nl()
        lv = 12345678
        lv >>= 16
        txt.print_l(lv)
        txt.nl()
        lv = 12345678
        lv >>= 24
        txt.print_l(lv)
        txt.nl()
        txt.nl()

        lv = -12345678
        lv >>= 8
        txt.print_l(lv)
        txt.nl()
        lv = -12345678
        lv >>= 16
        txt.print_l(lv)
        txt.nl()
        lv = -12345678
        lv >>= 24
        txt.print_l(lv)
        txt.nl()
    }
}
