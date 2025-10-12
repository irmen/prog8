%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1, lv2, lv3

        cx16.r0 = 12345
        txt.print_l(cx16.r0)
        txt.spc()
        cx16.r0s = -9999
        txt.print_l(cx16.r0)
        txt.nl()

        txt.print("sgns:\n")
        word @shared w1, w2, w3
        w1 = $1100
        w2 = $ff00 as word
        w3 = $0000
        txt.print_b(sgn(w1))
        txt.spc()
        txt.print_b(sgn(w2))
        txt.spc()
        txt.print_b(sgn(w3))
        txt.nl()

        lv1= 333333
        lv2 = -22222
        lv3 = 0
        txt.print_b(sgn(lv1))
        txt.spc()
        txt.print_b(sgn(lv2))
        txt.spc()
        txt.print_b(sgn(lv3))
        txt.nl()


;        txt.print_l(conv.str_l(0))      ; TODO fix crash
;        txt.print_l(conv.str_l(987654))      ; TODO fix crash
;        txt.print_l(conv.str_l(-12345))      ; TODO fix crash

        lv1 = 999999
        lv2 = 555555
        lv3 = 222222

        txt.print_bool(lv1 >= lv2+4*lv3)

        txt.nl()
    }
}
