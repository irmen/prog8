%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword @shared derp=2000
        derp[1000] = %00111100
        derp[1001] = %01111110

        ror(@(3000))
        txt.print_ubbin(derp[1000], true)
        txt.spc()
        sys.clear_carry()
        rol(@(3000))
        txt.print_ubbin(derp[1000], true)
        txt.nl()

        ror(derp[1000])
        txt.print_ubbin(derp[1000], true)
        txt.spc()
        sys.clear_carry()
        rol(derp[1000])
        txt.print_ubbin(derp[1000], true)
        txt.nl()

        ror(@(derp +1000))
        txt.print_ubbin(derp[1000], true)
        txt.spc()
        sys.clear_carry()
        rol(@(derp +1000))
        txt.print_ubbin(derp[1000], true)
        txt.nl()

        ^^uword a = derp+1000
        ror(a^^)
        txt.print_uwbin(a^^, true)
        txt.nl()
        sys.clear_carry()
        rol(a^^)
        txt.print_uwbin(a^^, true)
        txt.nl()
    }
}
