%import floats
%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print_b(sys.MIN_BYTE)
        txt.spc()
        txt.print_b(sys.MAX_BYTE)
        txt.nl()
        txt.print_ub(sys.MIN_UBYTE)
        txt.spc()
        txt.print_ub(sys.MAX_UBYTE)
        txt.nl()
        txt.print_w(sys.MIN_WORD)
        txt.spc()
        txt.print_w(sys.MAX_WORD)
        txt.nl()
        txt.print_uw(sys.MIN_UWORD)
        txt.spc()
        txt.print_uw(sys.MAX_UWORD)
        txt.nl()
        txt.nl()

        floats.print(floats.EPSILON)
        txt.nl()
        floats.print(floats.MIN)
        txt.nl()
        floats.print(floats.MAX)
        txt.nl()
        floats.print(floats.E)
        txt.nl()
        txt.print_ub(floats.SIZEOF)
        txt.nl()
        txt.print_ub(sys.SIZEOF_FLOAT)
        txt.nl()
    }
}
