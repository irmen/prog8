%import math
%import monogfx

main {
    sub start() {
        monogfx.hires()
        monogfx.fillrect(100, 100, 200, 100, true)

        sys.wait(60)

        monogfx.drawmode(monogfx.MODE_INVERT)

        monogfx.text(150,120,true, sc:"Hello World The Quick Brown Fox")
        monogfx.text(150,130,true, sc:"Jumps Over The Lazy Dog 1234567")

        sys.wait(60)

        monogfx.circle(150, 120, 80, true)
        monogfx.line(10, 20, 250, 160, true)

        repeat 500 {
            monogfx.plot( math.rnd(), math.rnd(), true)
        }

        repeat  {}
    }
}
