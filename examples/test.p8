%import conv
%import gfx2

main {

    sub start() {
        gfx2.screen_mode(6)     ; high res 4 colors
        gfx2.text_charset(3)
        gfx2.text(10, 10, 1, @"Hello!")

        c64.SETTIM(0,0,0)

        repeat 1 {
            uword xx
            ; gfx2.monochrome_stipple(true)
;            for xx in 0 to 639 {
;                gfx2.vertical_line(xx, 20, 220, 1)
;            }
;            gfx2.monochrome_stipple(false)
            for xx in 0 to 639 {
                gfx2.vertical_line(xx, 20, 450, 1)
            }
            for xx in 0 to 639 {
                gfx2.vertical_line(xx, 20, 450, 2)
            }
            for xx in 0 to 639 {
                gfx2.vertical_line(xx, 20, 450, 3)
            }
            for xx in 0 to 639 {
                gfx2.vertical_line(xx, 20, 450, 0)
            }
        }

        uword time = c64.RDTIM16()
        conv.str_uw(time)
        gfx2.text(100, 10, 1, conv.string_out)

        repeat {
        }
    }
}
