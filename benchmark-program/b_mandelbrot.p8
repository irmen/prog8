%import textio
%import floats

mandelbrot {
    const ubyte width = 39
    const ubyte height = 29
    const ubyte max_iter = 15

    sub calc(uword max_time) -> uword  {
        uword num_pixels
        ubyte pixelx
        ubyte pixely

        txt.home()
        cbm.SETTIM(0,0,0)

        while cbm.RDTIM16() < max_time {
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
                    num_pixels++

                    if cbm.RDTIM16()>=max_time
                        goto finished
                }
                txt.nl()
            }

            txt.clear_screen()
        }

finished:
        txt.color2(1, 6)
        return num_pixels
    }
}
