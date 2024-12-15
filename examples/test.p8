%import textio
%import palette
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        palette.set_c64pepto()
        sys.wait(100)
        palette.set_default16()
    }
}
