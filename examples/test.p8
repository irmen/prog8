%import textio
%zeropage basicsafe

main {
    sub start() {
        uword zz = $ea45
        txt.print_uwhex(zz, true)
        txt.nl()

        ;@(&zz) = $11
        setlsb(zz, 0)
        txt.print_uwhex(zz, true)
        txt.nl()
        ;@(&zz+1) = $22
        setmsb(zz, 0)
        txt.print_uwhex(zz, true)
        txt.nl()
        txt.nl()

        uword[] @split array = [$1234,$5678,$abcd]     ; TODO also with @split

        ubyte one = 1
        ubyte two = 2
        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(array[2], true)
        txt.nl()
        ;@(&array+one*2) = $ff
        ;@(&array+two*2+1) = $ff
        setlsb(array[one],0)
        setmsb(array[two],0)
        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(array[2], true)
        txt.nl()
    }
}
