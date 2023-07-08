%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        byte intensity = -25
        txt.print_b(intensity)
        txt.nl()
        txt.print_b(abs(intensity))
        intensity = abs(intensity)
        txt.nl()
        txt.print_b(intensity)
        txt.nl()

        txt.print_uw0(12345)
        txt.nl()
        word intensityw = -12345
        txt.print_w(intensityw)
        txt.nl()
        txt.print_w(abs(intensityw))
        intensityw = abs(intensityw)
        txt.nl()
        txt.print_w(intensityw)
    }
}
