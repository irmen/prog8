%import math
%import textio
%zeropage basicsafe

; Note: this program can be compiled for multiple target systems.

main {
    const uword screenwidth = txt.DEFAULT_WIDTH
    const uword screenheight = txt.DEFAULT_HEIGHT
    uword anglex
    uword angley
    ubyte color

    sub start()  {
        repeat {
            ubyte x = msb(math.sin8u(msb(anglex)) * screenwidth)
            ubyte y = msb(math.cos8u(msb(angley)) * screenheight)
            txt.setcc(x, y, 81, color)
            anglex+=366
            angley+=291
            color++
        }
    }
}
