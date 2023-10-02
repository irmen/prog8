%import textio
%import emudbg
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        txt.print_ub(emudbg.is_emulator())
        txt.nl()
        emudbg.console_value1(123)
        emudbg.console_value2(222)
        for cx16.r0L in iso:"Hello debug console!\n"
            emudbg.console_chrout(cx16.r0L)

        emudbg.console_write(iso:"Hello another message!\n")

        emudbg.EMU_DBG_HOTKEY_ENABLED=false
    }
}

