%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1 = -123456

        txt.print_l(abs(lv1))
        txt.spc()

        lv1 = -99999
        lv1 = abs(lv1)
        txt.print_l(abs(lv1))
        txt.nl()

        cx16.r4 = $1122
        cx16.r5 = $abcd
        txt.print_ulhex(mklong(cx16.r5H,cx16.r5L,cx16.r4H,cx16.r4L), true)
        txt.spc()
        txt.print_ulhex(mklong2(cx16.r5,cx16.r4), true)
        txt.nl()

    }
}
