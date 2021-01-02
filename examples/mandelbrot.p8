%import textio
%import floats
%import test_stack
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {
    const uword width = 30
    const uword height = 20
    const ubyte max_iter = 16

    sub start()  {
        txt.print("calculating mandelbrot fractal...")
        c64.SETTIM(0, 0, 0)

        ubyte pixelx
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
                txt.setcc(pixelx+4, pixely+1, 160, max_iter-iter)
            }
        }

        float duration = (c64.RDTIM16() as float) / 60
        txt.plot(0, 21)
        txt.print("finished in ")
        floats.print_f(duration)
        txt.print(" seconds!\n")

        ; test_stack.test()
    }
}
