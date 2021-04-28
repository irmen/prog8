%import conv
%import gfx2
%import textio

main {

    sub start() {
        gfx2.screen_mode(6)     ; highres 4c
        gfx2.text_charset(3)
        gfx2.text(10, 10, 1, @"Hello!")

        c64.SETTIM(0,0,0)

        ubyte yy
        uword rw

        repeat 20000 {
            rw = rndw()
            yy = (lsb(rw) & 127) + 20
            gfx2.plot(msb(rw), yy, 1)
        }
        repeat 20000 {
            rw = rndw()
            yy = (lsb(rw) & 127) + 20
            gfx2.plot(msb(rw), yy, 2)
        }
        repeat 20000 {
            rw = rndw()
            yy = (lsb(rw) & 127) + 20
            gfx2.plot(msb(rw), yy, 3)
        }
        repeat 50000 {
            rw = rndw()
            yy = (lsb(rw) & 127) + 20
            gfx2.plot(msb(rw), yy, 0)
        }

        uword time = c64.RDTIM16()
        conv.str_uw(time)
        gfx2.text(100, 10, 1, conv.string_out)

        repeat {
        }
    }
}
