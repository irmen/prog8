%import textio
%import strings
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1,lv2
        ^^long lptr = 20000
        uword @shared addr = &lv2
        ubyte @shared bytevar

        lv2 = $aabbccdd
        pokel(2000, $11223344)
        pokel(2000, lv2)
        pokel(addr, $11223344)
        pokel(addr, lv2)
        pokel(&lv2, $99887766)
        pokel(&lv2+4, $99887766)
        pokew(&addr, 9999)
        pokew(&addr+4, 9999)
        poke(&bytevar, 99)
        poke(&bytevar+4, 99)
        @(&bytevar) = 99
        @(&bytevar+4) = 99

        txt.print_ulhex(lv2, true)
        txt.nl()
    }
}
