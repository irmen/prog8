%import c64utils

main {

    const uword width = 40
    const uword height = 25

    struct Ball {
        uword anglex
        uword angley
        ubyte color
    }

    sub start()  {

        Ball ball

        forever {
            ubyte x = msb(sin8u(msb(ball.anglex)) as uword * width)
            ubyte y = msb(cos8u(msb(ball.angley)) as uword * height)
            c64scr.setcc(x, y, 81, ball.color)

            ball.anglex+=800
            ball.angley+=947
            ball.color++
        }
    }
}
