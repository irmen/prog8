%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        @($2005) = 0
        txt.print_ub(get_indexed_byte($2000, 5))
        txt.nl()
        @($2005) = 123
        txt.print_ub(get_indexed_byte($2000, 5))
        txt.nl()

    }

    sub  get_indexed_byte(uword pointer @R0, ubyte index @R1) -> ubyte {
        return @(cx16.r0 + cx16.r1L)
    }
}
