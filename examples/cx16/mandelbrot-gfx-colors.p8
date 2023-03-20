%import textio
%import floats
%zeropage basicsafe

main {
    const uword width = 256
    const uword height = 240
    const ubyte max_iter = 16       ; 32 actually looks pretty nice but takes longer

    sub start()  {
        initialize()
        mandel()
        repeat {
            ; do nothing
        }
    }

    sub mandel() {
        const float XL=-2.200
        const float XU=0.800
        const float YL=-1.300
        const float YU=1.300
        const float dx = (XU-XL)/width
        const float dy = (YU-YL)/height
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

                while iter<max_iter and xsquared+ysquared<4.0 {
                    y = x*y*2.0 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                }
                cx16.FB_set_pixel(max_iter-iter)
            }

            print_time()
        }
    }

    sub initialize() {
        void cx16.screen_mode($80, false)

        txt.plot(32, 5)
        txt.print("256*240")
        txt.plot(32, 6)
        txt.print("mandel-")
        txt.plot(33, 7)
        txt.print("brot")
        txt.plot(32, 9)
        txt.print("floats")
        txt.plot(32, 10)
        txt.print_b(max_iter)
        txt.print(" iter")

        cx16.clock_set_date_time(0, 0, 0, 0)

        cx16.r0=0
        cx16.FB_init()
    }

    sub print_time() {
        void cx16.clock_get_date_time()
        txt.plot(33, 12)
        if lsb(cx16.r2) < 10
            c64.CHROUT('0')
        txt.print_ub(lsb(cx16.r2))
        c64.CHROUT(':')
        if msb(cx16.r2) < 10
            c64.CHROUT('0')
        txt.print_ub(msb(cx16.r2))
    }
}
