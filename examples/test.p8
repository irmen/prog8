%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ;sys.exit(99)
        ;sys.exit2(99,123,200)
        sys.exit3(99,123,200,true)
    }
}
