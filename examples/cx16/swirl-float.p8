%import cx16textio
%import cx16flt
%zeropage basicsafe

main {

    const uword width = 80
    const uword height = 60

    struct Ball {
        float t
        ubyte color
    }

    sub start()  {

        Ball ball

        repeat {
            ubyte xx=(sin(ball.t) * width/2.1) + width/2.0 as ubyte
            ubyte yy=(cos(ball.t*1.1356) * height/2.1) + height/2.0 as ubyte
            txt.setcc(xx, yy, 81, ball.color)
            ball.t  += 0.05
            ball.color++
        }
    }
}
