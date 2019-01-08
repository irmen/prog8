%import c64utils

~ main {

    const uword width = 40
    const uword height = 25

    sub start()  {

        uword anglex
        uword angley
        ubyte color

        while true {
            ubyte x = msb(sin8u(msb(anglex)) as uword * width)
            ubyte y = msb(cos8u(msb(angley)) as uword * height)
            c64scr.setcc(x, y, 81, color)

            anglex+=800
            angley+=947
            color++
        }
    }
}
