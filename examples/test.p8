%import graphics
%import floats


main {
    sub start() {

        graphics.enable_bitmap_mode()

        uword xx
        ubyte yy

        graphics.line(150,50,150,50)

        for yy in 0 to 199-60 step 16 {

            for xx in 0 to 319-50 step 16 {
                graphics.line(30+xx, 10+yy, 50+xx, 30+yy)
                graphics.line(49+xx, 30+yy, 10+xx, 30+yy)
                graphics.line(11+xx, 29+yy, 29+xx, 11+yy)

                ; triangle 2, counter-clockwise
                graphics.line(30+xx, 40+yy, 10+xx, 60+yy)
                graphics.line(11+xx, 60+yy, 50+xx, 60+yy)
                graphics.line(49+xx, 59+yy, 31+xx,41+yy)
            }
        }

        repeat {
        }
    }
}
