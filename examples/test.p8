%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {
    sub start() {
        repeat {
            if cbm.GETIN2()==27
                cx16.poweroff_system()
            if cbm.GETIN2()==27
                cx16.reset_system()
        }
    }
}
