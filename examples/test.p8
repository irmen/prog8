%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword func1 = &function1
        uword func2 = &function2
        txt.print_uw(call(func1))
        void call(func1)
        txt.print_uw(call(func2))
        void call(func2)
    }

    sub function1() {
        txt.print("function1\n")
    }

    sub function2() -> uword {
        txt.print("function1\n")
        return 12345
    }
}
