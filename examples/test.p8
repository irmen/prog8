%import math
%import monogfx
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        monogfx.lores()
        monogfx.text_charset(1)

        ubyte angle
        for angle in 0 to 255 step 10 {
            monogfx.text(math.sin8u(angle), math.cos8u(angle)/2,true, iso:"Hello!")
        }
        repeat {
        }
    }
}
