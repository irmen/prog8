%option enable_floats

~ main {

    sub start() -> ()   {

        const word width = 320 // 2
        const word height = 256 // 2
        const word xoffset = 40
        const word yoffset = 20
        word pixelx
        byte pixely
        float xx
        float yy
        float x
        float y
        float xsquared
        float ysquared
        byte iter
        word plotx
        byte ploty

        _vm_gfx_clearscr(11)
        _vm_gfx_text(5, 5, 7, "Calculating Mandelbrot Fractal...")

        for pixely in yoffset to yoffset+height-1 {
            yy = flt((pixely-yoffset))/height/3.6+0.4

            for pixelx in xoffset to xoffset+width-1 {
                xx = flt((pixelx-xoffset))/width/3+0.2

                x = 0.0
                y = 0.0
                xsquared = 0
                ysquared = 0
                iter = 0
                while (iter<32 and xsquared+ysquared<4) {
                    y = x*y*2 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                }

                _vm_gfx_pixel(pixelx, pixely, iter)
            }
        }

        _vm_gfx_text(110, 160, 7, "Finished!")
    }

}
