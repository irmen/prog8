%import sprites
%import palette
%import math
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        word[128] xpos
        word[128] ypos

        for cx16.r2L in 0 to 127 {
            sprites.init(cx16.r2L, 0, 0, sprites.SIZE_8, sprites.SIZE_8, sprites.COLORS_16, 0)
            xpos[cx16.r2L] = math.rndw() & 511 as word + 64
            ypos[cx16.r2L] = math.rnd()
        }

        repeat {
            sys.waitvsync()
            palette.set_color(6, $f00)

            sprites.pos_batch(0, 128, &xpos, &ypos)

            palette.set_color(6, $0f0)
            for cx16.r2L in 0 to 127 {
                if cx16.r2L & 1 == 0 {
                    xpos[cx16.r2L] ++
                    if xpos[cx16.r2L] > 640
                        xpos[cx16.r2L] = 0
                } else {
                    ypos[cx16.r2L] ++
                    if ypos[cx16.r2L] > 480
                        ypos[cx16.r2L] = 0
                }
            }
            palette.set_color(6, $00f)
        }
    }
}
