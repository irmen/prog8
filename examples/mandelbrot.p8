%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


~ main {
    const uword width = 30
    const uword height = 20
    const ubyte max_iter = 16

    sub start()  {
        c64scr.print("calculating mandelbrot fractal...")

        c64.TIME_HI=0
        c64.TIME_MID=0
        c64.TIME_LO=0

        for ubyte pixely in 0 to height-1 {
            float yy = (pixely as float)/0.4/height - 1.0

            for ubyte pixelx in 0 to width-1 {
                float xx = (pixelx as float)/0.3/width - 2.2

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
                c64scr.setcc(pixelx+4, pixely+1, 160, max_iter-iter)
            }
        }

        float duration = floor(((c64.TIME_LO as float)
                                + 256.0*(c64.TIME_MID as float)
                                + 65536.0*(c64.TIME_HI as float))/60.0)
        c64scr.plot(0, 21)
        c64scr.print("finished in ")
        c64flt.print_f(duration)
        c64scr.print(" seconds!\n")
    }
}
