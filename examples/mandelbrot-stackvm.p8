%import c64utils
%import c64flt

~ main {
    const uword width = 320 // 2
    const uword height = 256 // 2
    const uword xoffset = 40
    const uword yoffset = 30

    sub start()  {
        vm_gfx_clearscr(11)
        vm_gfx_text(2, 1, 1, "Calculating Mandelbrot Fractal...")

        for ubyte pixely in yoffset to yoffset+height-1 {
            float yy = (pixely-yoffset as float)/3.6/height+0.4

            for uword pixelx in xoffset to xoffset+width-1 {
                float xx = (pixelx-xoffset as float)/3.0/width+0.2

                float xsquared = 0.0
                float ysquared = 0.0
                float x = 0.0
                float y = 0.0
                ubyte iter = 0

                while (iter<32 and xsquared+ysquared<4.0) {
                    y = x*y*2.0 + yy
                    x = xsquared - ysquared + xx
                    xsquared = x*x
                    ysquared = y*y
                    iter++
                }

                vm_gfx_pixel(pixelx, pixely, iter)
            }
        }
        vm_gfx_text(11, 21, 1, "Finished!")
    }
}
