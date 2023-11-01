%import syslib
%import gfx2
%import math

main {
    sub start() {
        void cx16.screen_mode(128, false)

        ubyte color
        repeat {
            cx16.FB_cursor_position(math.rnd(), math.rnd())
            cx16.FB_set_pixel(color)
            color++
        }
    }
}
