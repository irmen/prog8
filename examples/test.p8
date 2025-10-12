%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv1, lv2, lv3

        lv1 = 999999
        lv2 = 555555
        lv3 = 222222

        txt.print_l(lv3 | $2222)
        txt.nl()

        lv1 = lv2-(lv3 | $2222)
        txt.print_l(lv1)
        txt.spc()
        txt.print_ulhex(lv1, true)
    }
}
