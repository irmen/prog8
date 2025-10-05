%import gfx_lores

main {
    sub start() {
        gfx_lores.graphics_mode()

        uword radius
        for radius in 1 to 10 {
            gfx_lores.circle(30*radius, 100, lsb(radius), 1)
            gfx_lores.disc(30*radius, 130, lsb(radius), 1)
            gfx_lores.safe_disc(30*radius, 160, lsb(radius), 1)
        }

        repeat {}
    }
}
