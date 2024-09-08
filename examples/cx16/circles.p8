%import gfx2
%import math


main {
    const ubyte MAX_NUM_CIRCLES = 80
    const ubyte GROWTH_RATE = 2
    uword[MAX_NUM_CIRCLES] @split circle_x
    uword[MAX_NUM_CIRCLES] @split circle_y
    ubyte[MAX_NUM_CIRCLES] circle_radius
    ubyte num_circles = 0
    ubyte background_color

    sub start() {
        gfx2.screen_mode(1)

        repeat {
            background_color = math.rnd()
            gfx2.clear_screen(background_color)
            num_circles = 0
            draw_circles()
        }
    }

    sub draw_circles() {
        uword @zp x
        uword @zp y
        ubyte @zp radius

        while num_circles<MAX_NUM_CIRCLES {
            x = math.rndw() % gfx2.width
            y = math.rndw() % gfx2.height
            radius = GROWTH_RATE * 2        ; use a bit of a buffer between circles.
            if not_colliding() {
                radius -= GROWTH_RATE
                ubyte color = math.rnd()
                while color==background_color
                    color = math.rnd()
                while not_edge() and not_colliding() {
                    gfx2.disc(x, y as ubyte, radius, color)
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
            if x + radius >= gfx2.width
                return false
            if y as word - radius < 0
                return false
            if y + radius >= gfx2.height
                return false
            return true
        }
    }
}
