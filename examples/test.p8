%import math
%import monogfx

main {
    sub start() {
        monogfx.lores()
        monogfx.drawmode(monogfx.MODE_INVERT)

        uword x1, x2
        uword y1, y2

        repeat 200 {
            x1 = math.rnd()
            y1 = math.rnd() % 240
            x2 = math.rnd()
            y2 = math.rnd() % 240
            monogfx.line(x1, y1, x2, y2, true)
        }

        repeat 5 {
            for cx16.r9L in 0 to 200 {
                monogfx.vertical_line(cx16.r9L, 10, 200, true)
            }
        }


        monogfx.disc(160, 120, 100, true)
        monogfx.fillrect(20, 100, 280, 50, true)
        monogfx.drawmode(monogfx.MODE_STIPPLE)
        monogfx.fillrect(80, 10, 50, 220, true)

        repeat {
        }
    }
}
