%import c64utils
%import c64flt

~ main {

    const uword width = 40
    const uword height = 25

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
            draw_edges()
            anglex+=1000
            angley+=433
            anglez+=907
            c64.PLOT(0,0,0)
            c64scr.print("3d cube! (integer) ")
            c64scr.print_ub(c64.TIME_LO)
            c64scr.print(" jiffies/frame")
            c64.TIME_LO=0
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

        word Axx = wcosa*wcosb // 128
        word Axy = (wcosa_sinb*wsinc - wsina*wcosc) // 128
        word Axz = (wcosa_sinb*wcosc + wsina*wsinc) // 128
        word Ayx = wsina*wcosb // 128
        word Ayy = (wsina_sinb*wsinc + wcosa*wcosc) // 128
        word Ayz = (wsina_sinb*wcosc - wcosa*wsinc) // 128
        word Azx = -wsinb
        word Azy = wcosb*wsinc // 128
        word Azz = wcosb*wcosc // 128

        for ubyte i in 0 to len(xcoor)-1 {
            rotatedx[i] = (Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]) // 128
            rotatedy[i] =(Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]) // 128
            rotatedz[i] = (Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]) // 128
        }
    }

    sub draw_edges() {

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz >= 10 {
                const float height_f = height
                float persp = (rz as float + 180.0)/height_f
                byte sx = rotatedx[i] as float / persp as byte + width//2
                byte sy = rotatedy[i] as float / persp as byte + height//2
                ; @todo switch to this once idiv_w is implemented: (and remove c64flt import)
                ;word persp = (rz+180) // height
                ;byte sx = rotatedx[i] // persp as byte + width//2
                ;byte sy = rotatedy[i] // persp as byte + height//2
                c64scr.setchrclr(sx as ubyte, sy as ubyte, 46, i+2)
            }
        }

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz < 10 {
                const float height_f = height
                float persp = (rz as float + 180.0)/height_f
                byte sx = rotatedx[i] as float / persp as byte + width//2
                byte sy = rotatedy[i] as float / persp as byte + height//2
                ; @todo switch to this once idiv_w is implemented: (and remove c64flt import)
                ;word persp = (rz+180) // height
                ;byte sx = rotatedx[i] // persp as byte + width//2
                ;byte sy = rotatedy[i] // persp as byte + height//2
                c64scr.setchrclr(sx as ubyte, sy as ubyte, 81, i+2)
            }
        }
    }
}
