%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1, lv2, lv3

        ;txt.print_l(cx16.r0)            ; TODO fix crash
        ;txt.print_l(conv.str_l(0))      ; TODO fix crash

        lv1 = 999999
        lv2 = 555555
        lv3 = 222222

        txt.print_bool(lv1 >= lv2+4*lv3)

        txt.nl()
    }
}
