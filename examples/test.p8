%import gfx2

main {
    sub start() {
        gfx2.screen_mode(2)

        gfx2.safe_circle(120, 140, 200, 1)
        sys.wait(30)
        gfx2.safe_circle(520, 140, 200, 1)
        sys.wait(30)
        gfx2.safe_circle(120, 340, 200, 1)
        sys.wait(30)
        gfx2.safe_circle(520, 340, 200, 1)
        sys.wait(30)

        gfx2.safe_disc(120, 140, 200, 1)
        sys.wait(30)
        gfx2.safe_disc(520, 140, 200, 1)
        sys.wait(30)
        gfx2.safe_disc(120, 340, 200, 1)
        sys.wait(30)
        gfx2.safe_disc(520, 340, 200, 1)

        repeat {
        }
    }
}
