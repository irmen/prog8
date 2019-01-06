%import c64utils
%import c64flt

~ main {

    const uword width = 40
    const uword height = 25
    const float height_f = height

    ; vertices
    byte[8] xcoor = [ -40, -40, -40, -40,  40,  40,  40, 40 ]
    byte[8] ycoor = [ -40, -40,  40,  40, -40, -40,  40, 40 ]
    byte[8] zcoor = [ -40,  40, -40,  40, -40,  40, -40, 40 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx
    word[len(ycoor)] rotatedy
    word[len(zcoor)] rotatedz

    sub start()  {
        uword anglex
        uword angley
        uword anglez
        while(true) {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            c64scr.clear_screenchars(32)
            c64scr.print("\uf1203d cube!")
            draw_edges()
            anglex+=1000
            angley+=433
            anglez+=907
        }
    }

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word wcosa = cos8(ax) as word
        word wsina = sin8(ax) as word
        word wcosb = cos8(ay) as word
        word wsinb = sin8(ay) as word
        word wcosc = cos8(az) as word
        word wsinc = sin8(az) as word

        word wcosa_sinb = wcosa*wsinb // 128
        word wsina_sinb = wsina*wsinb // 128

        word Axx = (wcosa*wcosb as float / 128.0) as word
        word Axy = ((wcosa_sinb*wsinc - wsina*wcosc) as float / 128.0) as word
        word Axz = ((wcosa_sinb*wcosc + wsina*wsinc) as float / 128.0) as word
        word Ayx = (wsina*wcosb as float / 128.0) as word
        word Ayy = ((wsina_sinb*wsinc + wcosa*wcosc) as float / 128.0) as word
        word Ayz = ((wsina_sinb*wcosc - wcosa*wsinc) as float / 128.0) as word
        word Azx = -wsinb
        word Azy = (wcosb*wsinc as float / 128.0) as word
        word Azz = (wcosb*wcosc as float / 128.0) as word

        for ubyte i in 0 to len(xcoor)-1 {
            word xc = xcoor[i] as word
            word yc = ycoor[i] as word
            word zc = zcoor[i] as word
            word zz = (Axx*xc + Axy*yc + Axz*zc) // 128     ;   @todo bugs when not using 'zz' temporary var!?
            rotatedx[i] = zz
            zz=(Ayx*xc + Ayy*yc + Ayz*zc) // 128
            rotatedy[i] = zz
            zz = (Azx*xc + Azy*yc + Azz*zc) // 128
            rotatedz[i] = zz
        }
    }

    sub draw_edges() {

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz >= 10 {
                float persp = (rz as float + 180.0)/height_f
                byte sx = rotatedx[i] as float / persp as byte + width//2
                byte sy = rotatedy[i] as float / persp as byte + height//2
                c64scr.setchrclr(sx as ubyte, sy as ubyte, 46, i+2)
            }
        }

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz < 10 {
                float persp = (rz as float + 180.0)/height_f
                byte sx = rotatedx[i] as float / persp as byte + width//2
                byte sy = rotatedy[i] as float / persp as byte + height//2
                c64scr.setchrclr(sx as ubyte, sy as ubyte, 81, i+2)
            }
        }
    }
}
