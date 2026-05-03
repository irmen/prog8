%zeropage basicsafe
%option no_sysinit
%import textio

main {
    ubyte @shared msb1 = $12
    ubyte @shared b2 = $34
    ubyte @shared b1 = $56
    ubyte @shared lsb1 = $78
    uword @shared msw_val = $1234
    uword @shared lsw_val = $abcd

    sub start() {
        ; Test mklong(msb, b2, b1, lsb) -> long variable
        long @shared result1
        result1 = mklong(msb1, b2, b1, lsb1)
        ; Expected: $12345678

        ; Test mklong2(msw, lsw) -> long variable
        long @shared result2
        result2 = mklong2(msw_val, lsw_val)
        ; Expected: $1234abcd

        ; Test peekl(const_address) -> long variable
        ; We'll set up some test data in memory first
        long @shared testdata = $aabbccdd
        long @shared result3
        result3 = peekl(&testdata)
        ; Expected: $aabbccdd

        ; Test peekl(pointer) -> long variable
        ubyte[4] testarray = [$11, $22, $33, $44]
        long @shared result4
        result4 = peekl(&testarray)
        ; Expected: $44332211 (peekl reads 4 bytes as long: lsb=$11, byte=$22, byte=$33, msb=$44)
        ; Print results for verification
        txt.print_ulhex(result1, true)      ; expected: $12345678
        txt.nl()
        txt.print_ulhex(result2, true)      ; expected: $1234abcd
        txt.nl()
        txt.print_ulhex(result3, true)      ; expected: $aabbccdd
        txt.nl()
        txt.print_ulhex(result4, true)      ; expected: $44332211
        txt.nl()

        ;sys.poweroff_system()
    }
}
