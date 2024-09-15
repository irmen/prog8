%import monogfx
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        monogfx.hires()
        monogfx.circle(320, 240, 200, true)
        sys.wait(60)
        monogfx.drawmode(monogfx.MODE_STIPPLE)
        monogfx.disc(320, 240, 200, true)
        sys.wait(60)
        monogfx.drawmode(monogfx.MODE_NORMAL)
        monogfx.safe_disc(320, 240, 200, true)

        repeat {
        }
    }
}
