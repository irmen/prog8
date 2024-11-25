%import textio
%zeropage basicsafe

main {
    sub start() {
        foo(42)
        foo(42)
        foo(42)
        bar(9999)
        bar(9999)
        bar(9999)
        baz(42, 123)
        baz(42, 123)
        baz(42, 123)
        meh(42, 9999)
        meh(42, 9999)
        meh(42, 9999)
    }

    sub foo(ubyte arg @R0) {
        txt.print_ub(arg)
        txt.nl()
    }

    sub bar(uword arg @R0) {
        txt.print_uw(arg)
        txt.nl()
    }

    sub baz(ubyte arg1 @R0, ubyte arg2 @R1) {
        txt.print_ub(arg1)
        txt.spc()
        txt.print_ub(arg2)
        txt.nl()
    }

    sub meh(ubyte arg1 @R0, uword arg2 @R1) {
        txt.print_ub(arg1)
        txt.spc()
        txt.print_uw(arg2)
        txt.nl()
    }
}
