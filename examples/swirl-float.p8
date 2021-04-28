%import textio
%import floats
%zeropage floatsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        float ball_t
        ubyte ball_color

        repeat {
            ubyte xx=(sin(ball_t) * txt.DEFAULT_WIDTH/2.1) + txt.DEFAULT_WIDTH/2.0 as ubyte
            ubyte yy=(cos(ball_t*1.1356) * txt.DEFAULT_HEIGHT/2.1) + txt.DEFAULT_HEIGHT/2.0 as ubyte
            txt.setcc(xx, yy, 81, ball_color)
            ball_t  += 0.08
            ball_color++
        }
    }
}
