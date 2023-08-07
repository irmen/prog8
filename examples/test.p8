%import gfx2
%zeropage dontuse

main {
    sub start () {
        gfx2.screen_mode(5)
        gfx2.monochrome_stipple(false)
        for cx16.r0 in 100 to 200 {
            for cx16.r1 in 100 to 110 {
                gfx2.plot(cx16.r0, cx16.r1, 1)
            }
        }

        gfx2.monochrome_stipple(true)
        for cx16.r0 in 100 to 200 {
            for cx16.r1 in 110 to 120 {
                gfx2.plot(cx16.r0, cx16.r1, 1)
            }
        }

        gfx2.monochrome_stipple(true)
        for cx16.r0 in 110 to 190 {
            for cx16.r1 in 105 to 115 {
                gfx2.plot(cx16.r0, cx16.r1, 0)
            }
        }

        gfx2.disc(320, 240, 140, 1)
        gfx2.monochrome_stipple(false)
        gfx2.disc(320, 240, 100, 0)
    }
}
