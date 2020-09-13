%target cx16
%import cx16textio
%import cx16flt
%zeropage basicsafe

; TODO fix this, only black squares output...


main {
    const uword width = 60
    const uword height = 50
    const ubyte max_iter = 16

    sub start()  {
        txt.print("calculating mandelbrot fractal...\n\n")

        ubyte pixelx
        ubyte pixely

        for pixely in 0 to height-1 {
            float yy = (pixely as float)/0.40/height - 1.3

            for pixelx in 0 to width-1 {
                float xx = (pixelx as float)/0.32/width - 2.2

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
                ; txt.setchr(pixelx, pixely, '*')
                txt.color2(1, max_iter-iter)
                c64.CHROUT(' ')
            }
            c64.CHROUT('\n')
        }
    }
}
