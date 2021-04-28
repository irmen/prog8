%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {
    const uword screenwidth = txt.DEFAULT_WIDTH
    const uword screenheight = txt.DEFAULT_HEIGHT
    uword anglex
    uword angley
    ubyte color

    sub start()  {
        repeat {
            ubyte x = msb(sin8u(msb(anglex)) * screenwidth)
            ubyte y = msb(cos8u(msb(angley)) * screenheight)
            txt.setcc(x, y, 81, color)
            anglex+=366
            angley+=291
            color++
        }
    }
}
