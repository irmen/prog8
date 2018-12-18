%import c64utils
%option enable_floats

~ main {

    const uword width = 40
    const uword height = 25

    sub start()  {

        float t
        ubyte color

        while(1) {
            float x = sin(t) + cos(t*1.1234)
            float y = cos(t) + sin(t*1.44)
            c64scr.setchrclr(screenx(x), screeny(y), 81, color)
            t  += 0.1
            color++
        }
    }

    sub screenx(x: float) -> ubyte {
        return b2ub(fintb(x * flt(width)/4.2) + width//2)
    }
    sub screeny(y: float) -> ubyte {
        return b2ub(fintb(y * flt(height)/4.2) + height//2)
    }
}
