%import textio
%zeropage basicsafe

main {
    sub start() {
        foo(42)
        foo2(42)
        bar(9999,55)
        bar2(9999,55)
        txt.nl()
    }

    sub foo(ubyte arg) {
        txt.print_ub(arg)
        txt.nl()
    }

    sub foo2(ubyte arg @R2) {
        txt.print_ub(arg)
        txt.nl()
    }

    sub bar(uword arg, ubyte arg2) {
        txt.print_uw(arg)
        txt.spc()
        txt.print_ub(arg2)
        txt.nl()
    }

    sub bar2(uword arg @R0, ubyte arg2 @R1) {
        txt.print_uw(arg)
        txt.spc()
        txt.print_ub(arg2)
        txt.nl()
    }
}
