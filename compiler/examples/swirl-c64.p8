%import c64utils

~ main {

    const uword width = 40
    const uword height = 25

    sub start()  {

        uword anglex
        uword angley
        ubyte color

        while true {
            word x = sin8(msb(anglex)) as word
            word y = cos8(msb(angley)) as word
            ubyte xx=msb(x*39) + 20  ; -127..127 -> 0..39
            ubyte yy=msb(y*24) + 12  ; -127..127 -> 0..24
            c64scr.setcc(xx, yy, 81, color)

            anglex+=800
            angley+=947
            color++
        }
    }
}
