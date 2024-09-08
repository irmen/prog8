%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[2] array = [$1111,$eeee]
        uword[2] @split sarray = [$1111,$eeee]

        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(sarray[1], true)
        txt.nl()

        setmsb(array[1], $55)
        setmsb(sarray[1], $55)
        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(sarray[1], true)
        txt.nl()

        setlsb(array[1], $44)
        setlsb(sarray[1], $44)
        txt.print_uwhex(array[1], true)
        txt.nl()
        txt.print_uwhex(sarray[1], true)
        txt.nl()
    }
}
