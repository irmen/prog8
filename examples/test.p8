%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared zz = 12345

        zz <<= 9
        txt.print_l(zz)
        txt.nl()
        zz = 12345
        zz <<= 17
        txt.print_l(zz)
        txt.nl()
        zz = 12
        zz <<= 25
        txt.print_l(zz)
        txt.nl()
    }
}
