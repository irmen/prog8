%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        uword xx
        cx16.r1L = lsb(xx) & 3
    }
}
