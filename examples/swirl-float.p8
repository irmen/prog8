%import c64utils
%import c64flt

main {

    const uword width = 40
    const uword height = 25

    sub start()  {

        float t
        ubyte color

        forever {
            float x = sin(t)
            float y = cos(t*1.1356)
            ubyte xx=(x * width/2.2) + width/2.0 as ubyte
            ubyte yy=(y * height/2.2) + height/2.0 as ubyte
            c64scr.setcc(xx, yy, 81, color)
            t  += 0.08
            color++
        }
    }
}
