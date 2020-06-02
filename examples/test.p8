%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {
    sub start() {
        line(1111,22,3333,22)
        line(1111,22,1111,22)
        line(1111,22,666,22)

        line(1111,22,1111,33)
        line(1111,22,1111,22)
        line(1111,22,1111,11)
    }

    sub line(uword x1, ubyte y1, uword x2, ubyte y2) {
        word dx
        word dy
        byte ix = 1
        byte iy = 1
        if x2>x1 {
            dx = x2-x1 as word
        } else {
            ix = -1
            dx = x1-x2 as word
        }
        if y2>y1 {
            dy = y2-y1
        } else {
            iy = -1
            dy = y1-y2
        }
        word dx2 = 2 * dx
        word dy2 = 2 * dy
        word d = 0
        plotx = x1

        c64scr.print("dx=")
        c64scr.print_w(dx)
        c64scr.print("  ix=")
        c64scr.print_b(ix)
        c64.CHROUT('\n')
        c64scr.print("dy=")
        c64scr.print_w(dy)
        c64scr.print("  iy=")
        c64scr.print_b(iy)
        c64.CHROUT('\n')
    }

    uword plotx

}


