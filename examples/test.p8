%import conv
%import gfx2
%import textio

main {

    sub start() {
        gfx2.screen_mode(6)     ; highres 4c
        gfx2.text_charset(3)
        gfx2.text(10, 10, 1, @"Hello!")

        c64.SETTIM(0,0,0)

        ubyte rr
        for rr in 0 to 2 {
            uword yy
            for yy in 20 to 420 {
                gfx2.horizontal_line(10, yy, 610, (lsb(yy>>3) + rr) & 3)
            }
        }

        uword time = c64.RDTIM16()
        conv.str_uw(time)
        gfx2.text(100, 10, 1, conv.string_out)

        repeat {
        }
    }
}
