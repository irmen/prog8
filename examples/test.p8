%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        cx16.r0L = cx16.r1L
        cx16.r1=9999
        cx16.r2=9999
    }
}
