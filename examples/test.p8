%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        if not unexistingsymbol
            cx16.r0++
    }
}
