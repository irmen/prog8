%import c64lib
%import c64utils

main {

    const uword width = 40
    const uword height = 25

    ; vertices
    byte[] xcoor = [ -40, -40, -40, -40,  40,  40,  40, 40 ]
    byte[] ycoor = [ -40, -40,  40,  40, -40, -40,  40, 40 ]
    byte[] zcoor = [ -40,  40, -40,  40, -40,  40, -40, 40 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx
    word[len(ycoor)] rotatedy
    word[len(zcoor)] rotatedz

    sub start()  {

        uword anglex
        uword angley
        uword anglez
        word rz=33
        while(true) {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            c64scr.clear_screenchars(32)
            draw_edges()
            anglex+=1000
            angley+=433
            anglez+=907
            c64scr.plot(0,0)
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

        word wcosa_sinb = wcosa*wsinb / 128
        word wsina_sinb = wsina*wsinb / 128

        word Axx = wcosa*wcosb / 128
        word Axy = (wcosa_sinb*wsinc - wsina*wcosc) / 128
        word Axz = (wcosa_sinb*wcosc + wsina*wsinc) / 128
        word Ayx = wsina*wcosb / 128
        word Ayy = (wsina_sinb*wsinc + wcosa*wcosc) / 128
        word Ayz = (wsina_sinb*wcosc - wcosa*wsinc) / 128
        word Azx = -wsinb
        word Azy = wcosb*wsinc / 128
        word Azz = wcosb*wcosc / 128

        for ubyte i in 0 to len(xcoor)-1 {
            rotatedx[i] = (Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]) / 128
            rotatedy[i] = (Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]) / 128
            rotatedz[i] = (Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]) / 128
        }
    }

    ubyte[] vertexcolors = [1,7,7,12,11,6]

    sub draw_edges() {

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        ubyte i
        word rz
        word persp
        byte sx
        byte sy

        for i in 0 to len(xcoor)-1 {
            rz = rotatedz[i]
            if rz >= 10 {
                persp = (rz+200) / height
                sx = rotatedx[i] / persp as byte + width/2
                sy = rotatedy[i] / persp as byte + height/2
                c64scr.setcc(sx as ubyte, sy as ubyte, 46, vertexcolors[(rz as byte >>5) + 3])
            }
        }

        for i in 0 to len(xcoor)-1 {
            rz = rotatedz[i]
            if rz < 10 {
                persp = (rz+200) / height
                sx = rotatedx[i] / persp as byte + width/2
                sy = rotatedy[i] / persp as byte + height/2
                c64scr.setcc(sx as ubyte, sy as ubyte, 81, vertexcolors[(rz as byte >>5) + 3])
            }
        }
    }
}
