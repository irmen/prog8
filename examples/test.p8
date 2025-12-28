%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        long v = $12345678
        ubyte @shared ubv = 15

        v <<= 31 - ubv

        txt.print_ulhex(v, true)
    }
}
