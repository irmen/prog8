%option no_sysinit ; leave the CX16 defaults in place
%zeropage basicsafe ; don't step on BASIC zero page locations
%import textio

main {
    sub start() {
        long @shared l

        ;l = 2147483648

        l = $21111111
        testdec(l)
        l = $20000000
        testdec(l)
        l = 4026531840
        testdec(l)
        txt.nl()

        l = $21111111
        testinc(l)
        l = $2fffffff
        testinc(l)
    }

    sub testdec(long l) {
        txt.print_ulhex(l, true)
        txt.spc()
        txt.print_l(l)
        txt.nl()

        l++
        txt.print_ulhex(l, true)
        txt.spc()
        txt.print_l(l)
        txt.nl()

        l--
        l--
        txt.print_ulhex(l, true)
        txt.spc()
        txt.print_l(l)
        txt.nl()
        txt.nl()
    }

    sub testinc(long l) {
        txt.print_ulhex(l, true)
        txt.spc()
        txt.print_l(l)
        txt.nl()

        l++
        txt.print_ulhex(l, true)
        txt.spc()
        txt.print_l(l)
        txt.nl()
    }
}
