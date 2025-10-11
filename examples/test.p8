%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv1, lv2, lv3

        lv1 = 999999
        lv2 = 555555
        lv3 = 222222
        lv1 = lv2-(lv3*2)
        txt.print_l(lv1)
    }
}
