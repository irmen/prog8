%import textio
%zeropage basicsafe

main {

    sub start() {
        cx16.r0 = $1234
        cx16.r15 = $ea31

        txt.print_uwhex(cx16.r0, true)
        txt.print_uwhex(cx16.r15, true)
        txt.nl()

        cx16.save_virtual_registers()
        cx16.r0 = $3333
        cx16.r15 = $4444

        txt.print_uwhex(cx16.r0, true)
        txt.print_uwhex(cx16.r15, true)
        txt.nl()
        cx16.restore_virtual_registers()

        txt.print_uwhex(cx16.r0, true)
        txt.print_uwhex(cx16.r15, true)
        txt.nl()
        repeat {
        }
    }
}

