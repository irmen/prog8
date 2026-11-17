%import math
%import textio
%import petgfx

; Note: this program can be compiled for multiple target systems.

main {
    const uword screenwidth = 2 * txt.DEFAULT_WIDTH
    const uword screenheight = 2 * txt.DEFAULT_HEIGHT

    sub start() {
        uword anglex, angley
        ubyte x, y

        repeat {
            x = msb((math.sin8u(msb(anglex)) as uword) * screenwidth)
            y = msb((math.cos8u(msb(angley)) as uword) * screenheight)
            petgfx.plot(x, y)
            anglex+=375
            angley+=291
        }
    }
}
