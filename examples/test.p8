%import textio
%zeropage basicsafe

main {
    struct element {
        ubyte type
        long  l
    }

    sub start() {
        ^^element ptr = 5000
        ptr^^.l = 123456789
        txt.print_l(ptr^^.l)
        txt.nl()

        long @shared l1 = 1111110
        long @shared l2 = 2222220
        long @shared l3 = 3333330

        ptr.l &= l1+1
        txt.print_l(ptr^^.l)
        txt.nl()

        ptr.l |= l2+2
        txt.print_l(ptr^^.l)
        txt.nl()

        ptr.l ^= l3+3
        txt.print_l(ptr^^.l)
        txt.nl()
    }
}
