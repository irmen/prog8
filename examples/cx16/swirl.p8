%import cx16lib
%import cx16textio
%zeropage basicsafe

main {

    const uword width = 79
    const uword height = 59

    struct Ball {
        uword anglex
        uword angley
        ubyte color
    }

    sub start()  {

        Ball ball

        repeat {
            ubyte x = msb(sin8u(msb(ball.anglex)) as uword * width)
            ubyte y = msb(cos8u(msb(ball.angley)) as uword * height)
            txt.setcc(x, y, 81, ball.color)

            ball.anglex+=266
            ball.angley+=215
            ball.color++
        }
    }
}
