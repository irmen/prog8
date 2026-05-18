%import textio
%zeropage basicsafe

main {
    sub start() {
        &uword ptr = &cx16.r0

        cx16.r0 = 12345

        txt.print_uwhex(ptr, true)
    }
}
