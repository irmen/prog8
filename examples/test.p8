%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv = $aabbccdd

        @(&lv as ^^ubyte + 1) = 0

        txt.print_ubhex(lsb(msw($11223344)), true)
        txt.nl()
        txt.print_ubhex(lsb(msw(lv)),true)
        txt.nl()
        txt.print_ubhex(msb(lsw(lv)),true)
        txt.nl()

;        txt.print_ubhex(bsb($11223344),true)
;        txt.nl()
;        txt.print_ubhex(bsb(lv),true)
;        txt.nl()

        ;setbsb(lv, $99)

        setlsb(lv, $44)
        setmsb(lv, $11)
        txt.print_ulhex(lv, true)
        txt.nl()
        setlsb(lv, 0)
        setmsb(lv, 0)
        txt.print_ulhex(lv, true)
        txt.nl()

        long[32] longs
        longs[3]=$aabbccdd
        setmsb(longs[3], $11)
        setlsb(longs[3], $44)
        txt.print_ulhex(longs[3], true)
        txt.nl()
    }
}
