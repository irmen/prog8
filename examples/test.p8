%import textio
%zeropage basicsafe
%option no_sysinit
%encoding iso

main {
    sub start() {
        long @shared @nozp size = 147320
        while size>0 {
            size--
            if size & $ff == 0 {
                txt.chrout('.')
            }
        }
        txt.nl()
        ;;sys.poweroff_system()
    }
}
