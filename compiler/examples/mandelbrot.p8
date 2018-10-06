%option enable_floats

~ main {
    const word width = 320 // 2
    const word height = 256 // 2
    const word xoffset = 40
    const word yoffset = 30

    sub start()  {
        _vm_gfx_clearscr(11)
        _vm_gfx_text(2, 1, 1, "Calculating Mandelbrot Fractal...")

        for byte pixely in yoffset to yoffset+height-1 {
            float yy = flt((pixely-yoffset))/height/3.6+0.4

            for word pixelx in xoffset to xoffset+width-1 {
                float xx = flt((pixelx-xoffset))/width/3.0+0.2
                float xsquared
                float ysquared
                float x
                float y
                byte iter  ; @todo re-initialize variable when entering scope

                x = 0.0
                y = 0.0
                xsquared = 0
                ysquared = 0
                iter = 0
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

    memory byte jiffyclockHi = $a0
    memory byte jiffyclockMid = $a1
    memory byte jiffyclockLo = $a2

sub irq()  {
    _vm_gfx_pixel(jiffyclockLo,190,jiffyclockHi)
    _vm_gfx_pixel(jiffyclockLo,191,jiffyclockMid)
    _vm_gfx_pixel(jiffyclockLo,192,jiffyclockLo)
    return
}
}
