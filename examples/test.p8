%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        sys.exit3(100,101,102,true)
        sys.exit2(100,101,102)
        sys.exit(100)
    }
}
