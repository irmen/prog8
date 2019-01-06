%import c64utils

~ main {

    sub start()  {

        ubyte i =0
loop:
        byte s = sin8(i)
        ubyte su = sin8u(i)
        word sw = sin16(i)
        uword swu = sin16u(i)
        byte c = cos8(i)
        ubyte cu = cos8u(i)
        word cw = cos16(i)
        uword cwu = cos16u(i)
        c64scr.print_b(c)
        c64.CHROUT(' ')
        c64scr.print_w(cw)
        c64.CHROUT('\n')
        i++
        if_nz goto loop
    }
}
