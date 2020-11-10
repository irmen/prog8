%import textio
%import floats
%zeropage floatsafe

; Note: this program is compatible with C64 and CX16.

main {

    struct Ball {
        float t
        ubyte color
    }

    sub start()  {

        Ball ball

        repeat {
            ubyte xx=(sin(ball.t) * txt.DEFAULT_WIDTH/2.1) + txt.DEFAULT_WIDTH/2.0 as ubyte
            ubyte yy=(cos(ball.t*1.1356) * txt.DEFAULT_HEIGHT/2.1) + txt.DEFAULT_HEIGHT/2.0 as ubyte
            txt.setcc(xx, yy, 81, ball.color)
            ball.t  += 0.08
            ball.color++
        }
    }
}
