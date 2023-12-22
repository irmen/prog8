%import textio
%zeropage basicsafe

main {
    sub start() {
        uword function = &test
        uword @shared derp = call(function)
        txt.print_uw(derp)
        txt.nl()
        void call(function)
    }

    sub test() -> uword {
        txt.print("test\n")
        cx16.r0++
        return 999
    }
}
