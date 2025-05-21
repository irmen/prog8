%import math
%import monogfx

main {
    sub start() {
        monogfx.hires()
        monogfx.fillrect(100, 100, 200, 100, true)

        sys.wait(60)

        monogfx.drawmode(monogfx.MODE_INVERT)
        monogfx.circle(150, 120, 80, true)           ; TODO INVERT is BROKEN
        monogfx.line(10, 20, 250, 160, true)        ; TODO INVERT is BROKEN

        repeat 500 {
            monogfx.plot( math.rnd(), math.rnd(), true)     ; TODO INVERT is BROKEN
        }

        repeat  {}
    }
}
