%import gfx_lores
%import math

circles {
    const ubyte MAX_NUM_CIRCLES = 80
    const ubyte GROWTH_RATE = 4
    uword[MAX_NUM_CIRCLES] circle_x
    uword[MAX_NUM_CIRCLES] circle_y
    ubyte[MAX_NUM_CIRCLES] circle_radius
    ubyte color
    uword total_num_circles

    sub draw(bool use_kernal, uword max_time) -> uword {
        if use_kernal
            void cx16.set_screen_mode(128)
        else
            gfx_lores.graphics_mode()

        math.rndseed(12345,6789)
        cbm.SETTIM(0,0,0)

        total_num_circles = 0
        color = 16

        while cbm.RDTIM16()<max_time {
            if use_kernal {
                cx16.GRAPH_set_colors(0,0,0)
                cx16.GRAPH_clear()
            }
            else
                gfx_lores.clear_screen(0)
            total_num_circles += draw_circles(use_kernal, max_time)
        }

        if use_kernal
            void cx16.set_screen_mode(3)
        else {
            gfx_lores.text_mode()
        }

        return total_num_circles
    }

    sub draw_circles(bool use_kernal, uword max_time) -> uword {
        uword @zp x
        uword @zp y
        ubyte @zp radius

        ubyte num_circles

        while num_circles<MAX_NUM_CIRCLES and cbm.RDTIM16()<max_time {
            x = math.rndw() % 320
            y = math.rndw() % 240
            radius = GROWTH_RATE
            if not_colliding() {
                while not_edge() and not_colliding() {
                    radius += GROWTH_RATE
                }
                radius -= GROWTH_RATE
                if radius>0 {
                    color++
                    if color==0
                        color=16
                    if use_kernal {
                        cx16.GRAPH_set_colors(color, 255-color, 0)
                        cx16.GRAPH_draw_oval(x-radius, y-radius, radius*2, radius*2, true)
                    }
                    else
                        gfx_lores.disc(x, y as ubyte, radius, color)
                    circle_x[num_circles] = x
                    circle_y[num_circles] = y
                    circle_radius[num_circles] = radius
                    num_circles++
                }
            }
        }

        return num_circles

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
            if x + radius >= 320
                return false
            if y as word - radius < 0
                return false
            if y + radius >= 240
                return false
            return true
        }
    }
}
