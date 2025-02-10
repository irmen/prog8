%import textio
%zeropage basicsafe

main {

    sub start() {
        func($11,$22,$33,$44)
    }

    sub func(ubyte arg1, ubyte arg2 @R1, ubyte arg3 @R2, ubyte arg4) {
        txt.print_ubhex(arg1, false)
        txt.print_ubhex(arg2, false)
        txt.print_ubhex(arg3, false)
        txt.print_ubhex(arg4, false)
    }
}
