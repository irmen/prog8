%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_ulhex(&start, true)       ; this works
        txt.nl()

        txt.print_ulhex(&data.name, true)        ; this is ok
        txt.nl()
    }
}

data {
    %option amiga_chipram

    uword @shared thing
    str @shared name = "irmen"
}
