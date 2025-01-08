%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        uword a
        ubyte b
        bool c
        a=9999
        b=255
        c=false
        a, b, c = multi()
        txt.print_uw(a)
        txt.spc()
        txt.print_uw(b)
        txt.spc()
        txt.print_bool(c)
        txt.nl()
        a=9999
        b=255
        c=false
        a, void, c = multi()
        txt.print_uw(a)
        txt.spc()
        txt.print_uw(b)
        txt.spc()
        txt.print_bool(c)
        txt.nl()
        a=9999
        b=255
        c=false
        void, b, c = multi()
        txt.print_uw(a)
        txt.spc()
        txt.print_uw(b)
        txt.spc()
        txt.print_bool(c)
        txt.nl()
        a=9999
        b=255
        c=false
        void multi()
        txt.print_uw(a)
        txt.spc()
        txt.print_uw(b)
        txt.spc()
        txt.print_bool(c)
        txt.nl()

    }

    sub multi() -> uword, ubyte, bool {
        return 12345, 66, true
    }
}
