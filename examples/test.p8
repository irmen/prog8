%import c64flt
%zeropage basicsafe

main {

    sub start() {

        lsr(@(9999+A))
        ror(@(9999+A))
        rol(@(9999+A))
        ror2(@(9999+A))
        rol2(@(9999+A))

        c64scr.print_ub(X)

    }
}
