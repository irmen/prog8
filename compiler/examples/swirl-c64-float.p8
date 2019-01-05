%import c64utils
%option enable_floats

~ main {

    const uword width = 40
    const uword height = 25

    sub start()  {

        float t
        ubyte color

        while true {
            float x = sin(t)
            float y = cos(t*1.1356)
            ubyte xx=screenx(x)
            ubyte yy=screeny(y)

            ;c64.COLOR = color
            ;c64scr.PLOT(xx,yy)
            ;c64.CHROUT('Q')     ;  shift-q = filled circle
            c64scr.setchrclr(xx, yy, 81, color)

            t  += 0.08
            color++
        }
    }

    sub screenx(float x) -> ubyte {
        return (x * width/2.2) + width/2.0 as ubyte
    }
    sub screeny(float y) -> ubyte {
        return (y * height/2.2) + height/2.0 as ubyte
    }
}
