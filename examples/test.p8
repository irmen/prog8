%import textio
%option no_sysinit
%zeropage basicsafe

main {
    const ubyte VALUE = 123

    sub start() {
        uword @shared @nozp location = $4000

        @($3fff) = 55
        @($4000) = 56
        @($4001) = 57

        txt.print_ub(@($3fff))
        txt.spc()
        txt.print_ub(@($4000))
        txt.spc()
        txt.print_ub(@($4001))
        txt.nl()

        for location in $4000 to $4002 {
            @(location-1) = VALUE
            txt.print_ub(@($3fff))
            txt.spc()
            txt.print_ub(@($4000))
            txt.spc()
            txt.print_ub(@($4001))
            txt.nl()
        }
    }
}


/** TODO scoping bug
; scoping bug:


main {

    %option no_symbol_prefixing

    sub start() {
        other.something()
    }
}

other {
    sub something() {
    }
}
**/
