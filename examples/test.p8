%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        ubyte @shared v1,v2,v3
        v1 = %10011001
        v2 = %10101010
        v3 = %00111100

        v1 &= %00011111
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()

        v1 &= ~v2
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()

        v1 |= 100
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()

        v1 |= v2
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()

        v1 |= v2 & v3
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()

        v1 &= v2|v3
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()

        v1 &= ~(v2|v3)
        v1++
        txt.print_ubbin(v1, true)
        txt.nl()
    }
}
