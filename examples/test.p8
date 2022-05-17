%import math

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

        ; a "pixelshader":
        sys.gfx_enable(0)       ; enable lo res screen

        ubyte angle

        for angle in 0 to 180 {
            ubyte xx = math.sinr8u(angle)
            ubyte yy = math.cosr8u(angle) / 2
            sys.gfx_plot(xx, yy, 255)
        }

        repeat {
        }
    }
}
