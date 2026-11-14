%import gfx_hires
%import lineclip
%import math
%zeropage basicsafe

main {
    sub start() {
        gfx_hires.graphics_mode()
        gfx_hires.clear_screen(0)

        lineclip.set_cliprect(100, 70, 570, 410)
        ; rect() takes dimensions; these cover inclusive corners (99,69) to (571,411).
        gfx_hires.rect(99, 69, 473, 343, 1)

        uword seed = cbm.RDTIM16()
        math.rndseed(seed, seed ^ $9bdf)
        draw_random_lines()
    }

    sub draw_random_lines() {
        repeat {
            word x1 = (math.randrangew(2001) as word) - 1000
            word y1 = (math.randrangew(2001) as word) - 1000
            word x2 = (math.randrangew(2001) as word) - 1000
            word y2 = (math.randrangew(2001) as word) - 1000

            bool visible
            word clipped_x1, clipped_y1, clipped_x2, clipped_y2
            visible, clipped_x1, clipped_y1, clipped_x2, clipped_y2 = lineclip.clip(x1, y1, x2, y2)
            if visible
                gfx_hires.line(clipped_x1 as uword, clipped_y1 as uword, clipped_x2 as uword, clipped_y2 as uword, 2)
        }
    }
}
