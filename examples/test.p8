%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub set_color(ubyte dummy, uword arg) {
        arg++
    }

    sub start() {
        ubyte intens
        set_color(0, (intens >> 1) * $111)

        byte intensity = -25
        txt.print_b(intensity)
        txt.nl()
        txt.print_b(abs(intensity))
        intensity = abs(intensity)
        txt.nl()
        txt.print_b(intensity)
        txt.nl()
        word intensityw = 2555
        txt.print_uw0(12345)
        txt.nl()
        txt.print_w(intensityw)
        txt.nl()
        txt.print_w(abs(intensityw))
        intensityw = abs(intensityw)
        txt.nl()
        txt.print_w(intensityw)
    }
}
