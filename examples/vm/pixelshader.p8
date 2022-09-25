; NOTE: meant to test to virtual machine output target (use -target virtual)

main  {

    sub start() {

        ; a "pixelshader":
        sys.gfx_enable(0)       ; enable lo res screen
        ubyte shifter

        repeat {
            uword xx
            uword yy = 0
            repeat 240 {
                xx = 0
                repeat 320 {
                    sys.gfx_plot(xx, yy, xx*yy + shifter as ubyte)
                    xx++
                }
                yy++
            }
            shifter+=4

            sys.wait(1)
            sys.waitvsync()
        }
     }
}
