%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared current = cx16.screen_mode(0, true)
        ubyte @shared width
        ubyte @shared height
        txt.print_ub(current)
        txt.nl()
        cx16.set_screen_mode(128)
        %asm {{
            phx
            jsr  cx16.get_screen_mode
            sta  p8_current
            stx  p8_width
            sty  p8_height
            plx
        }}
        txt.print_ub(current)
        txt.spc()
        txt.print_ub(width)
        txt.spc()
        txt.print_ub(height)
        txt.nl()
        txt.nl()

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
