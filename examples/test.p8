%import textio
%option no_sysinit
%zeropage basicsafe
%encoding iso

main {
    sub start() {
        txt.iso()
        txt.print("hello world\n")
        ;; txt.print_uw(12345)
        txt.nl()
        sys.poweroff_system()
    }
}
