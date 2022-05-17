%import math

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

        ; a "pixelshader":
        sys.gfx_enable(0)       ; enable lo res screen

        ubyte angle

        for angle in 0 to 255 {
            ubyte xx = math.sin8u(angle)
            ubyte yy = math.cos8u(angle)
            sys.gfx_plot(xx, yy, 255)
        }

        repeat {
        }
    }
}
