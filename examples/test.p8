%zeropage basicsafe
%option no_sysinit

main {
    ubyte tw = text.width()
    sub start() {
        tw++
    }
}

text {
    sub width() -> ubyte {
        cx16.r0++
        return 80
    }
}
