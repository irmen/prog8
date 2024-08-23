%import gfx2
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {

    sub start() {
        gfx2.screen_mode(2)
        demofill()
    }

    sub demofill() {
        gfx2.circle(160, 120, 110, 1)
        gfx2.rect(180, 5, 25, 190, 2)
        gfx2.line(100, 150, 240, 10, 2)
        gfx2.rect(150, 130, 10, 100, 3)

        sys.wait(30)

        cbm.SETTIM(0,0,0)
        gfx2.fill(100,100,3)
        gfx2.fill(100,100,2)
        gfx2.fill(100,100,0)
        uword duration = cbm.RDTIM16()
        sys.wait(30)

        gfx2.screen_mode(0)
        txt.nl()
        txt.print_uw(duration)
        txt.print(" jiffies\n")

        ; hires 4c before optimizations: ~345 jiffies

    }
}
