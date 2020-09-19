%import c64textio
%zeropage basicsafe

; TODO this code is identical to the commanderx16 one except the import

main {

    struct Ball {
        uword anglex
        uword angley
        ubyte color
    }

    sub start()  {

        Ball ball

        repeat {
            ubyte x = msb(sin8u(msb(ball.anglex)) as uword * txt.DEFAULT_WIDTH)
            ubyte y = msb(cos8u(msb(ball.angley)) as uword * txt.DEFAULT_HEIGHT)
            txt.setcc(x, y, 81, ball.color)

            ball.anglex+=366
            ball.angley+=291
            ball.color++
        }
    }
}
