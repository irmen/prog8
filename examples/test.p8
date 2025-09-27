%import textio
%zeropage basicsafe

main {
    sub start() {

        long @shared lv1, lv2

        lv1 = $11223344
        lv2 = $22ffff22

        txt.print_ulhex(lv1 | $8080, true)
        txt.spc()
        txt.print_ulhex(lv1 & $f0f0, true)
        txt.spc()
        txt.print_ulhex(lv1 ^ $8f8f, true)
        txt.nl()

        cx16.r6 = $8080
        cx16.r7 = $f0f0
        cx16.r8 = $8f8f

        txt.print_ulhex(lv1 | cx16.r6, true)
        txt.spc()
        txt.print_ulhex(lv1 & cx16.r7, true)
        txt.spc()
        txt.print_ulhex(lv1 ^ cx16.r8, true)
        txt.nl()

        lv1 = $11223344
        lv2 = $22ffff22
        lv1 |= lv2
        txt.print_ulhex(lv1, true)
        txt.spc()
        lv1 = $11223344
        lv1 &= lv2
        txt.print_ulhex(lv1, true)
        txt.spc()
        lv1 = $11223344
        lv1 ^= lv2
        txt.print_ulhex(lv1, true)
        txt.nl()
    }
}
