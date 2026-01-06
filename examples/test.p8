%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_ubhex(lsb(msw($11223344)), true)
        txt.nl()
        long @shared lv = $aabbccdd
        txt.print_ubhex(lsb(msw(lv)),true)
        txt.nl()

        txt.print_ubhex(bsb($11223344),true)
        txt.nl()
        txt.print_ubhex(bsb(lv),true)
        txt.nl()
        txt.nl()
        txt.print_ubhex(bsb(cx16.r0),true)
        txt.nl()
        txt.print_ubhex(bsb(cx16.r0L),true)
        txt.nl()
    }
}
