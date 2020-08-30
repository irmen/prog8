%import cx16textio
%import cx16flt
%zeropage basicsafe

main {
    const uword width = 256
    const uword height = 200
    const ubyte max_iter = 32

    sub start()  {
        initialize()
        mandel()
        repeat {
            ; do nothing
        }
    }

    sub mandel() {
        ubyte pixelx
        ubyte pixely

        for pixely in 0 to height-1 {
            float yy = (pixely as float)/0.35/height - 1.35

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

            print_time()
        }
    }

    sub initialize() {
        void cx16.screen_set_mode($80)

        txt.plot(32, 5)
        txt.print("256*200")
        txt.plot(32, 6)
        txt.print("mandel-")
        txt.plot(33, 7)
        txt.print("brot")
        txt.plot(32, 9)
        txt.print("floats")
        txt.plot(32, 10)
        txt.print("32 iter")

        cx16.r0 = 0
        cx16.r1 = 0
        cx16.r2 = 0
        cx16.r3 = 0
        cx16.clock_set_date_time()

        cx16.r0=0
        cx16.FB_init()
    }

    sub print_time() {
        cx16.clock_get_date_time()
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
