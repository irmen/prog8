%import emudbg

main {
    sub start() {
        emudbg.console_write("start address: ")
        emudbg.console_hex_long(&start)
        emudbg.exitemu()
    }
}
