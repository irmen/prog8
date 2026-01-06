%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv = $aabbccdd
        @(&lv as ^^ubyte + 1) = 0
        @(&lv as ^^ubyte + 2) = $77

        txt.print_ulhex(lv, true)
    }
}
