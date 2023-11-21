%import textio
%zeropage basicsafe

main {
    sub start() {
        sys.save_prog8_internals()
        txt.print("ok\n")
        sys.restore_prog8_internals()
    }
}
