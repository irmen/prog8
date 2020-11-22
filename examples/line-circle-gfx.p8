%import graphics
%import test_stack

; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        graphics.enable_bitmap_mode()

        draw_lines()
        draw_circles()

        ; graphics.disable_bitmap_mode()
        ; test_stack.test()

        repeat {
        }
    }

    sub draw_circles() {
        ubyte xx
        for xx in 3 to 7 {
            graphics.circle(xx*50-100, 10+xx*16, (xx+6)*4)
            graphics.disc(xx*50-100, 10+xx*16, (xx+6)*2)
        }
    }

    sub draw_lines() {
        ubyte i
        for i in 0 to 255 step 4 {
            uword x1 = ((graphics.WIDTH-256)/2 as uword) + sin8u(i)
            uword y1 = (graphics.HEIGHT-128)/2 + cos8u(i)/2
            uword x2 = ((graphics.WIDTH-64)/2 as uword) + sin8u(i)/4
            uword y2 = (graphics.HEIGHT-64)/2 + cos8u(i)/4
            graphics.line(x1, lsb(y1), x2, lsb(y2))
        }
    }
}
