%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

; TODO why is this larger than on the previous compiler version?


main {
    const uword screenwidth = txt.DEFAULT_WIDTH
    const uword screenheight = txt.DEFAULT_HEIGHT
    struct Ball {
        uword anglex
        uword angley
        ubyte color
    }

    sub start()  {
        Ball ball
        repeat {
            ubyte x = msb(sin8u(msb(ball.anglex)) * screenwidth)
            ubyte y = msb(cos8u(msb(ball.angley)) * screenheight)
            txt.setcc(x, y, 81, ball.color)
            ball.anglex+=366
            ball.angley+=291
            ball.color++
        }
    }
}
