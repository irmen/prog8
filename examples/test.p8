%import textio
%import floats
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

        txt.print_f(floats.EPSILON)
        txt.nl()
        txt.print_f(sys.MIN_FLOAT)
        txt.nl()
        txt.print_f(sys.MAX_FLOAT)
        txt.nl()
        txt.print_f(floats.E)
        txt.nl()
        txt.print_ub(sys.SIZEOF_FLOAT)
        txt.nl()
    }
}
