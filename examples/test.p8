%option no_sysinit ; leave the CX16 defaults in place
%zeropage basicsafe ; don't step on BASIC zero page locations
%import textio

main {
    sub start() {
        long @shared l1, l2

        l1 = $7fffffff
        txt.print_ulhex(l1, true)
        txt.spc()
        txt.print_l(l1)
        txt.nl()

        l2 = $80000000
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        l1 = -2147483648
        txt.print_ulhex(l1, true)
        txt.spc()
        txt.print_l(l1)
        txt.nl()

        l2 = -2147483649     ; will be truncated
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        l1 = $abcd1234
        txt.print_ulhex(l1, true)
        txt.spc()
        txt.print_l(l1)
        txt.nl()

        l2 = -$7fffffff
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        l1 = $80000001
        txt.print_ulhex(l1, true)
        txt.spc()
        txt.print_l(l1)
        txt.nl()

        l2 = -$80
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        l2 ^= $80000000
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        l2 = $80
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        l2 |= $80000000
        txt.print_ulhex(l2, true)
        txt.spc()
        txt.print_l(l2)
        txt.nl()
    }
}
