%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        cx16.r0=0
        repeat 65536 {
            cx16.r0++
        }
        txt.print_uw(cx16.r0)
    }
}
