%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        &ubyte mmvar = $2000

        txt.print_ub(@($2000))
        txt.nl()
        @($2000) = 123
        txt.print_ub(@($2000))
        txt.nl()

        mmvar = 42
        txt.print_ub(@($2000))
        txt.nl()

        cx16.r0 = 123
    }
}
