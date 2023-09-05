%import graphics
%import math

; note: this program is tuned for the CX16, but with some minor modifications can run on other systems too.

main {
    const ubyte MAX_NUM_CIRCLES = 80
    const ubyte GROWTH_RATE = 2
    uword[MAX_NUM_CIRCLES] @split circle_x
    uword[MAX_NUM_CIRCLES] @split circle_y
    ubyte[MAX_NUM_CIRCLES] circle_radius
    ubyte num_circles = 0
    ubyte background_color

    sub start() {
        graphics.enable_bitmap_mode()

        repeat {
            background_color = math.rnd()
            graphics.clear_screen(0, background_color)
            num_circles = 0
            draw_circles()
        }
    }

    sub draw_circles() {
        uword @zp x
        uword @zp y
        ubyte @zp radius

        while num_circles<MAX_NUM_CIRCLES {
            x = math.rndw() % graphics.WIDTH
            y = math.rndw() % graphics.HEIGHT
            radius = GROWTH_RATE * 2        ; use a bit of a buffer between circles.
            if not_colliding() {
                radius -= GROWTH_RATE
                ubyte color = math.rnd()
                while color==background_color
                    color = math.rnd()
                graphics.colors(color, 0)
                while not_edge() and not_colliding() {
                    graphics.disc(x, y as ubyte, radius)
                    sys.waitvsync()
                    radius += GROWTH_RATE
                }
                circle_x[num_circles] = x
                circle_y[num_circles] = y
                circle_radius[num_circles] = radius - GROWTH_RATE
                num_circles++
            }
        }

        sub not_colliding() -> bool {
            if num_circles==0
                return true
            ubyte @zp c
            for c in 0 to num_circles-1 {
                if distance(c) < (radius as uword) + circle_radius[c]
                    return false
            }
            return true
        }

        sub distance(ubyte cix) -> uword {
            word dx = x as word - circle_x[cix]
            word dy = y as word - circle_y[cix]
            uword sqx = dx*dx as uword
            uword sqy = dy*dy as uword
            return sqrt(sqx + sqy)
        }

        sub not_edge() -> bool {
            if x as word - radius < 0
                return false
            if x + radius >= graphics.WIDTH
                return false
            if y as word - radius < 0
                return false
            if y + radius >= graphics.HEIGHT
                return false
            return true
        }
    }
}
