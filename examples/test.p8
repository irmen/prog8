%import monogfx

main {
    sub start() {
        monogfx.lores()
        monogfx.stipple(true)

        uword x1, x2
        uword y1, y2

        repeat {
            x1 = math.rnd()
            y1 = math.rnd() % 240
            x2 = math.rnd()
            y2 = math.rnd() % 240
            monogfx.line(x1, y1, x2, y2, true)
        }
    }
}
