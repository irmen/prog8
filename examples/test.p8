%import gfx2
%zeropage dontuse

main {
    sub start () {
        gfx2.screen_mode(5)
        gfx2.monochrome_stipple(true)
        gfx2.disc(320, 240, 140, 1)
        gfx2.monochrome_stipple(false)
        gfx2.disc(320, 240, 90, 1)
        gfx2.disc(320, 240, 40, 0)
    }
}
