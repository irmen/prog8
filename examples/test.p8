%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte derp=2
        callfar(0, &func, 0)
continue:
        txt.print("main again.")
    }

    sub func() {
        txt.print("func.")
    }
}
