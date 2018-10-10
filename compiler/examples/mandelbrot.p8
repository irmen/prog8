%option enable_floats

~ main {
    const uword width = 320 // 2
    const uword height = 256 // 2
    const uword xoffset = 40
    const uword yoffset = 30

    sub start()  {
        _vm_gfx_clearscr(11)
        _vm_gfx_text(2, 1, 1, "Calculating Mandelbrot Fractal...")

        for ubyte pixely in yoffset to yoffset+height-1 {
            float yy = flt((pixely-yoffset))/height/3.6+0.4

            for uword pixelx in xoffset to xoffset+width-1 {
                float xx = flt((pixelx-xoffset))/width/3.0+0.2

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

                _vm_gfx_pixel(pixelx, pixely, iter)
            }
        }
        _vm_gfx_text(11, 21, 1, "Finished!")
    }
}


; ---- some weird testing 60hz irq handling routine------

~ irq {

    memory ubyte jiffyclockHi = $a0
    memory ubyte jiffyclockMid = $a1
    memory ubyte jiffyclockLo = $a2

sub irq()  {
    _vm_gfx_pixel(jiffyclockLo,190,jiffyclockHi)
    _vm_gfx_pixel(jiffyclockLo,191,jiffyclockMid)
    _vm_gfx_pixel(jiffyclockLo,192,jiffyclockLo)
    return
}
}
