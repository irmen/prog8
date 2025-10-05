%import monogfx

main {
    sub start() {
        monogfx.lores()

        uword radius
        for radius in 1 to 10 {
            monogfx.circle(30*radius, 100, lsb(radius), true)
            monogfx.disc(30*radius, 130, lsb(radius), true)
            monogfx.safe_disc(30*radius, 160, lsb(radius), true)
        }

        repeat {}
    }
}
