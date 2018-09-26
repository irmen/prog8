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
        float xsq
        float ysq
        byte iter
        word plotx
        byte ploty

        _vm_gfx_clearscr(11)
        _vm_gfx_text(5, 5, 7, "Calculating Mandelbrot Fractal...")

        for pixely in yoffset to yoffset+height-1 {
            yy = flt((pixely-yoffset))/height/3.6+0.4          ; @todo why is /height/3.6 not const-folded???

            for pixelx in xoffset to xoffset+width-1 {
                xx = flt((pixelx-xoffset))/width/3+0.2      ; @todo why is /width/3 not const-folded???

                x = 0.0
                y = 0.0
                xsq = 0
                ysq = 0
                iter = 0
                while (iter<32 and xsq+ysq<4) {
                    y = x*y*2 + yy
                    x = xsq - ysq + xx
                    xsq = x*x
                    ysq = y*y
                    iter++
                }

                _vm_gfx_pixel(pixelx, pixely, iter)
            }
        }

        _vm_gfx_text(110, 160, 7, "Finished!")
    }

}
