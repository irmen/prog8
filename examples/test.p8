%zeropage basicsafe
%import textio
%import emudbg

main {
    sub start() {
        txt.print("is emulator? ")
        txt.print_ub(emudbg.is_emulator())
        txt.nl()

        emudbg.console_write(iso:"Hello from emulator.\n")
        emudbg.console_chrout(iso:'1')
        emudbg.console_chrout(iso:'2')
        emudbg.console_chrout(iso:'3')
        emudbg.console_chrout(iso:'\n')
        emudbg.console_value1(123)
        emudbg.console_chrout(iso:'\n')
        emudbg.console_value2(42)
        emudbg.console_chrout(iso:'\n')
    }
}
