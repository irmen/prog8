%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword @shared uw = 5555
        byte @shared bb = -44

        uw = (bb as ubyte) as uword
        txt.print_uw(uw)            ; 212
        txt.nl()

        uw = 8888 + (bb as ubyte)   ; TODO fix 6502 codegen
        txt.print_uw(uw)            ; 9100
        txt.nl()
    }
}
