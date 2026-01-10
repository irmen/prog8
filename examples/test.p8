%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub set_state(uword buffer) {
        uword addr = &buffer[2]
        addr++
    }

    sub start() {
        set_state(100)

        uword @shared ww
        cx16.r0L += 2

        ww = 2
        ww += 5
        ww += 256
        ww += $9500
        txt.print_uwhex(ww, true)
        txt.nl()

        ww = 2
        ww *= 3
        ww *= 4
        ww *= 5
        txt.print_uw(ww)
        txt.nl()

        ww = 0
        txt.print_uwhex(ww, true)
        txt.print_uwhex(ww + 5, true)
        txt.print_uwhex(ww + 256, true)
        txt.print_uwhex(ww + 512, true)
        txt.print_uwhex(ww + $9b00, true)
        txt.print_uwhex(ww + $9b22, true)
        txt.nl()
        txt.print_uwhex(ww - 5, true)
        txt.print_uwhex(ww - 256, true)
        txt.print_uwhex(ww - 512, true)
        txt.print_uwhex(ww - $9b00, true)
        txt.print_uwhex(ww - $9b22, true)
    }
}
