%import cx16flt
%zeropage basicsafe

main {
    const uword width = 256
    const uword height = 200
    const ubyte max_iter = 16

    sub start()  {

        void cx16.screen_set_mode($80)
        cx16.r0=0
        cx16.FB_init()

        ubyte pixelx
        ubyte pixely

        for pixely in 0 to height-1 {
            float yy = (pixely as float)/0.4/height - 1.0

            cx16.r0 = 0
            cx16.r1 = pixely
            cx16.FB_cursor_position()

            for pixelx in 0 to width-1 {
                float xx = (pixelx as float)/0.3/width - 2.2

                float xsquared = 0.0
                float ysquared = 0.0
                float x = 0.0
                float y = 0.0
                ubyte iter = 0

                while iter<max_iter and xsquared+ysquared<4.0 {
                    y = x*y*2.0 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                }
                cx16.FB_set_pixel(max_iter-iter)
            }
        }
    }
}
