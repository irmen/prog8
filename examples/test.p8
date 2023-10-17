%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        byte @shared bb = -44
        uword uw = 8888 + (bb as ubyte)
        txt.print_uw(uw)            ; 9100
        txt.nl()
        txt.print_uw(8888 + (bb as ubyte))            ; 9100
        txt.nl()
    }
}
