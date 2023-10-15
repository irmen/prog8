%import gfx2
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {

    sub start() {
        ubyte[] shifts = [0,2,4,6]
        ubyte value = 100
        ubyte index = 2
        value >>= shifts[index]
        txt.print_ub(value)
        txt.nl()
        value = 100
        index = 1
        value >>= shifts[index]
        txt.print_ub(value)
        txt.nl()
        value = 100
        index = 0
        value >>= shifts[index]
        txt.print_ub(value)
        txt.nl()

        gfx2.screen_mode(2)
        demofill()
        repeat {
        }
    }

    sub demofill() {
        gfx2.circle(160, 120, 110, 1)
        gfx2.rect(180, 5, 25, 190, 1)
        gfx2.line(100, 150, 240, 10, 1)
        gfx2.line(101, 150, 241, 10, 1)
        gfx2.fill(100,100,2)
        gfx2.fill(182,140,3)
        gfx2.fill(182,40,1)
    }
}
