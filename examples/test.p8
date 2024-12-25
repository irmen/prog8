%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        uword[2] array1

        array1[1] = $0122
        txt.print_uwhex(array1[1], true)
        txt.nl()
        rol(array1[1])
        txt.print_uwhex(array1[1], true)
        txt.nl()
        sys.set_carry()
        ror(array1[1])
        txt.print_uwhex(array1[1], true)
        txt.nl()
    }
}
