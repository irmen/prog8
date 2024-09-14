%import textio
%import floats
%zeropage basicsafe

main {
    const ubyte width = 60
    const ubyte height = 50
    const ubyte max_iter = 16

    sub start()  {
        txt.print("calculating mandelbrot fractal...\n\n")
        cbm.SETTIM(0,0,0)

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
                txt.color2(1, max_iter-iter)
                txt.spc()
            }
            txt.nl()
        }

        float duration = (cbm.RDTIM16() as float) / 60
        txt.print("\nfinished in ")
        floats.print(duration)
        txt.print(" seconds!\n")
    }
}
