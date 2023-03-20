%import textio
%import floats
%zeropage basicsafe

main {
    const uword width = 256
    const uword height = 240
    const ubyte max_iter = 16       ; 32 actually looks pretty nice but takes longer

    sub start()  {
        void cx16.screen_mode($80, false)
        cx16.r0=0
        cx16.FB_init()
        mandel()
    }

    sub mandel() {
        const float XL=-2.200
        const float XU=0.800
        const float YL=-1.300
        const float YU=1.300
        float dx = (XU-XL)/width
        float dy = (YU-YL)/height
        ubyte pixelx
        ubyte pixely

        for pixely in 0 to height-1 {
            float yy = YL+dy*(pixely as float)

            cx16.FB_cursor_position(0, pixely)

            for pixelx in 0 to width-1 {
                float xx = XL+dx*(pixelx as float)

                float xsquared = 0.0
                float ysquared = 0.0
                float x = 0.0
                float y = 0.0
                ubyte iter = 0

                while xsquared+ysquared<4.0 {
                    y = x*y*2.0 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                    if iter>16
                        break
                }
                cx16.FB_set_pixel(iter)
            }
        }
    }
}
