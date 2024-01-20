%import graphics
%import floats
%import test_stack
%zeropage floatsafe

; Draw a mandelbrot in graphics mode (the image will be 256 x 200 pixels).
; NOTE: this will take an eternity to draw on a real c64. A CommanderX16 is a bit faster.
; even in Vice in warp mode (700% speed on my machine) it's slow, but you can see progress

; Note: this program can be compiled for multiple target systems.

main {
    const uword width = 320
    const ubyte height = 200
    const ubyte max_iter = 16

    sub start()  {
        graphics.enable_bitmap_mode()

        uword pixelx
        ubyte pixely

        for pixely in 0 to height-1 {
            float yy = (pixely as float)/0.4/height - 1.0

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

                if iter & 1 !=0
                    graphics.plot(pixelx, pixely)
            }
        }

        ; graphics.disable_bitmap_mode()
        ; test_stack.test()

        repeat {
        }
    }
}
