%import monogfx
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {

    sub start() {
        monogfx.lores()
        demofill()
    }

    sub demofill() {
        monogfx.circle(160, 120, 110, true)
        monogfx.rect(180, 5, 25, 190, true)
        monogfx.line(100, 150, 240, 10, true)
        monogfx.line(101, 150, 241, 10, true)
        monogfx.rect(150, 130, 10, 100, true)

        sys.wait(30)

        cbm.SETTIM(0,0,0)
        monogfx.fill(100,100,true)
        monogfx.fill(100,100,false)
        uword duration = cbm.RDTIM16()
        sys.wait(30)

        monogfx.textmode()
        txt.nl()
        txt.print_uw(duration)
        txt.print(" jiffies\n")

        ; before optimizations: ~166 jiffies

    }
}
