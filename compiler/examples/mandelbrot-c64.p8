%import c64utils
%option enable_floats

~ main {
    const uword width = 30
    const uword height = 20
    const ubyte max_iter = 16

    sub start()  {
        c64scr.print("calculating mandelbrot fractal...\n")

        c64.TIME_HI=0
        c64.TIME_MID=0
        c64.TIME_LO=0

        for ubyte pixely in 0 to height-1 {
            float yy = flt(pixely)/height/0.4-1.0

            for ubyte pixelx in 0 to width-1 {
                float xx = flt(pixelx)/width/0.3-2.0

                float xsquared = 0.0
                float ysquared = 0.0
                float x = 0.0
                float y = 0.0
                ubyte iter = 0

                while (iter<max_iter and xsquared+ysquared<4.0) {
                    y = x*y*2.0 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                }

                c64.CHROUT(32+max_iter-iter)
            }
            c64.CHROUT('\n')
        }
        float duration = floor((c64.TIME_LO + c64.TIME_MID*256.0 + c64.TIME_HI*65536.0)/60.0)
        c64scr.print("finished in ")
        c64flt.print_f(duration)
        c64scr.print(" seconds!\n")
    }
}
