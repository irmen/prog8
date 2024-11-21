%import floats
%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        floats.print(floats.EPSILON)
        txt.nl()
        floats.print(floats.MIN_FLOAT)
        txt.nl()
        floats.print(floats.MAX_FLOAT)
        txt.nl()
        floats.print(floats.E)
        txt.nl()
        txt.print_ub(floats.SIZEOF)
        txt.nl()
    }
}
