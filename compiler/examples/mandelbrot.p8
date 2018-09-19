%option enable_floats

~ main {

    sub start() -> ()   {

        const word width = 320
        const word height = 256
        word pixelx
        byte pixely
        float xx
        float yy
        float x
        float y
        float x2
        byte iter
        word plotx
        byte ploty

        _vm_write_str("Calculating Mandelbrot fractal, have patience...\n")
        _vm_gfx_clearscr(11)

        for pixely in 0 to height-1 {
            for pixelx in 0 to width-1 {
                xx = pixelx/width/3+0.2
                yy = pixely/height/3.6+0.4

                x = 0.0
                y = 0.0

                for iter in 0 to 31 {
                    if (x*x + y*y > 4) break
                    x2 = x*x - y*y + xx
                    y = x*y*2 + yy
                    x = x2
                }

                _vm_gfx_pixel(pixelx, pixely, iter)
;                plotx = pixelx*2
;                ploty = pixely*2
;                _vm_gfx_pixel(plotx, ploty, iter)
;                _vm_gfx_pixel(plotx+1, ploty, iter)
;                _vm_gfx_pixel(plotx, ploty+1, iter)
;                _vm_gfx_pixel(plotx+1, ploty+1, iter)
            }
        }

        _vm_gfx_text(5, 5, 7, "Mandelbrot Fractal")
    }

}
