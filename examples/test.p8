%import textio
%zeropage basicsafe

main {
    sub start() {
        uword zz = $ea45
        txt.print_uwhex(zz, true)
        txt.nl()

        setlsb(zz, $11)
        txt.print_uwhex(zz, true)
        txt.nl()
        setmsb(zz, $22)
        txt.print_uwhex(zz, true)
        txt.nl()
        txt.nl()

        uword[] array = [$1234,$5678,$abcd]     ; TODO also with @split

        ubyte one = 1
        ubyte two = 2
        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(array[2], true)
        txt.nl()
        setlsb(array[one],$ff)
        setmsb(array[two],$00)
        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(array[2], true)
        txt.nl()
    }
}
