%import graphics

main {
    const uword width = 320
    const ubyte height = 200
    const ubyte max_iter = 16

    sub start()  {
        graphics.enable_bitmap_mode()

        uword pixelx
        ubyte pixely

        for pixely in 0 to height-1 {
            for pixelx in 0 to width-1 {
                graphics.plot(pixelx, pixely)
            }
        }

        repeat {
        }
    }
}
