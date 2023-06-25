%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        if foobar1 {
            cx16.r0++
        }

        sys.exit(foobar3)
    }
}

