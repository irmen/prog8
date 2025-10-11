%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_l(0)
        txt.nl()
        txt.print(conv.str_l(0))
;        long lv1, lv2, lv3
;
;        lv1 = 999999
;        lv2 = 555555
;        lv3 = 222222
;        lv1 = lv2-(lv3 | $2222)
;        txt.print_l(lv1)
;        txt.spc()
;        txt.print_ulhex(lv1, true)
    }
}
