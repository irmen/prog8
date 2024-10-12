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
        const uword offsetx = 0
        const uword offsety = 0

        monogfx.circle(offsetx+160, offsety+120, 110, true)
        monogfx.rect(offsetx+180, offsety+5, 25, 190, true)
        monogfx.line(offsetx+100, offsety+150, offsetx+240, offsety+10, true)
        monogfx.line(offsetx+101, offsety+150, offsetx+241, offsety+10, true)
        monogfx.rect(offsetx+150, offsety+130, 10, 100, true)

        sys.wait(30)

        cbm.SETTIM(0,0,0)
        monogfx.fill(offsetx+100,offsety+100,true)
        monogfx.fill(offsetx+100,offsety+100,false)
        uword duration = cbm.RDTIM16()
        sys.wait(30)

        monogfx.textmode()
        txt.nl()
        txt.print_uw(duration)
        txt.print(" jiffies\n")

        ; before optimizations: ~166 jiffies

    }
}
