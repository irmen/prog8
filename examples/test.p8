%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword @shared large = (320*240/8/8)
        const uword WIDTH=320
        uword x1 = ((WIDTH-256)/2 as uword) + 200
        txt.print_uw(x1)
        txt.nl()
        x1 = ((WIDTH-256)/2) + 200
        txt.print_uw(x1)
    }
}

