%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print_uw(cx16.r0)
        txt.nl()
    }
}
