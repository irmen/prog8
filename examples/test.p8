%import textio
%zeropage basicsafe
%option no_sysinit

main {
    ubyte tw = other.width()
    sub start() {
        tw++
        txt.print_uw(tw)
    }
}

other {
    sub width() -> ubyte {
        cx16.r0++
        return 80
    }
}
