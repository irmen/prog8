%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {
    sub start() {
        repeat {
            if cbm.GETIN2()==27
                sys.poweroff_system()
        }
    }
}
