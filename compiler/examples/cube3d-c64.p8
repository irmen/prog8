%import c64utils
%option enable_floats       ; @todo needed for now to avoid compile error in c64lib

~ main {

    const uword width = 40
    const uword height = 25

    ; vertices
    byte[8] xcoor = [ -100, -100, -100, -100,  100,  100,  100, 100 ]
    byte[8] ycoor = [ -100, -100,  100,  100, -100, -100,  100, 100 ]
    byte[8] zcoor = [ -100,  100, -100,  100, -100,  100, -100, 100 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx=0
    word[len(ycoor)] rotatedy=0
    word[len(zcoor)] rotatedz=-32767

    sub start()  {
        uword anglex
        uword angley
        uword anglez
        while(true) {
            rotate_vertices(anglex, angley, anglez)
            ; c64.CLEARSCR()
            ; draw_edges()
            anglex+=256
            angley+=83
            anglez+=201
        }
    }

    sub rotate_vertices(uword ax, uword ay, uword az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word cosa = cos8(msb(ax)) as word
        word sina = sin8(msb(ax)) as word
        word cosb = cos8(msb(ay)) as word
        word sinb = sin8(msb(ay)) as word
        word cosc = cos8(msb(az)) as word
        word sinc = sin8(msb(az)) as word

        word cosa_sinb = msb(cosa*sinb)
        word sina_sinb = msb(sina*sinb)

        c64.CHROUT('>')
        c64scr.print_w(cosa_sinb)
        c64.CHROUT(',')
        c64scr.print_w(sina_sinb)
        c64.CHROUT('\n')

        word Axx = msb(cosa*cosb)
        word Axy = msb(cosa_sinb*sinc - sina*cosc)
        word Axz = msb(cosa_sinb*cosc + sina*sinc)
        word Ayx = msb(sina*cosb)
        word Ayy = msb(sina_sinb*sinc + cosa*cosc)
        word Ayz = msb(sina_sinb*cosc - cosa*sinc)
        word Azx = -sinb
        word Azy = msb(cosb*sinc)
        word Azz = msb(cosb*cosc)

        c64.CHROUT('>')
        c64scr.print_w(Axx)
        c64.CHROUT(',')
        c64scr.print_w(Axy)
        c64.CHROUT(',')
        c64scr.print_w(Axz)
        c64.CHROUT('\n')

        for ubyte i in 0 to len(xcoor)-1 {
            word xc = xcoor[i]
            word yc = ycoor[i]
            word zc = zcoor[i]
            rotatedx[i] = Axx*xc ;+ Axy*yc + Axz*zc      ; @todo wrong code generated? crash!
            rotatedy[i] = Ayx*xc ;+ Ayy*yc + Ayz*zc      ; @todo wrong code generated? crash!
            rotatedz[i] = Azx*xc ;+ Azy*yc + Azz*zc      ; @todo wrong code generated? crash!
        }
    }

    sub draw_edges() {

        sub toscreenx(word x, word z) -> ubyte {
            return msb(x) and 31
        }

        sub toscreeny(word y, word z) -> ubyte {
            return msb(y) and 15
        }

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz >= 100 {
                ubyte sx = toscreenx(rotatedx[i], rz)
                ubyte sy = toscreeny(rotatedy[i], rz)
                c64scr.setchrclr(sx, sy, 46, i+2)
            }
        }

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz < 100 {
                ubyte sx = toscreenx(rotatedx[i], rz)
                ubyte sy = toscreeny(rotatedy[i], rz)
                c64scr.setchrclr(sx, sy, 81, i+2)
            }
        }
    }
}
