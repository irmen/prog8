%import floats
%import gfx2
%import test_stack
%zeropage floatsafe

main {
    sub start() {
        gfx2.screen_mode(1)
        ;graphics.enable_bitmap_mode()

        uword xx
        ubyte yy

        gfx2.line(160,100,160,80 ,1)
        gfx2.line(160,80,180,81 ,1)
        gfx2.line(180,81,177,103 ,1)

        for yy in 0 to 199-60 step 16 {

            for xx in 0 to 319-50 step 16 {
                gfx2.line(30+xx, 10+yy, 50+xx, 30+yy ,1)
                gfx2.line(49+xx, 30+yy, 10+xx, 30+yy ,1)
                gfx2.line(11+xx, 29+yy, 29+xx, 11+yy ,1)

                ; triangle 2, counter-clockwise
                gfx2.line(30+xx, 40+yy, 10+xx, 60+yy ,1)
                gfx2.line(11+xx, 60+yy, 50+xx, 60+yy ,1)
                gfx2.line(49+xx, 59+yy, 31+xx,41+yy ,1)
            }
        }

        repeat {
        }
    }
}
