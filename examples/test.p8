%option no_sysinit
%zeropage basicsafe
%import textio
%import emudbg


main {
    sub start() {
        emudbg.console_write(iso:"hello1\x0a")
        emudbg.console_nl()
        emudbg.console_write(iso:"hello2\x0a")
        emudbg.console_nl()
        emudbg.console_write(iso:"hello3\x0a")
        emudbg.console_nl()
    }
}
