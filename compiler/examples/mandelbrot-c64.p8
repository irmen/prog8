%import c64utils
%option enable_floats

~ main {
    const uword width = 30
    const uword height = 20

    sub start()  {
        c64scr.print_string("calculating mandelbrot fractal...\n")

        for ubyte pixely in 0 to height-1 {
            float yy = pixely/flt(height)/0.4-1.0

            for ubyte pixelx in 0 to width-1 {
                float xx = pixelx/flt(width)/0.3-2.0

                float xsquared = 0.0
                float ysquared = 0.0
                float x = 0.0
                float y = 0.0
                ubyte iter = 0

                while (iter<16 and xsquared+ysquared<4.0) {
                    y = x*y*2.0 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                }

                c64.CHROUT(48-iter)
            }
            c64.CHROUT('\n')
        }
        c64scr.print_string("finished!\n")
    }
}
