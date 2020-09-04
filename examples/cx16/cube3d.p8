%import cx16textio
; TODO fix compilation when zeropage is not basicsafe
%zeropage basicsafe

main {

    const uword screen_width = 80
    const uword screen_height = 60

    ; vertices
    word[] xcoor = [ -40, -40, -40, -40,  40,  40,  40, 40 ]
    word[] ycoor = [ -40, -40,  40,  40, -40, -40,  40, 40 ]
    word[] zcoor = [ -40,  40, -40,  40, -40,  40, -40, 40 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx
    word[len(ycoor)] rotatedy
    word[len(zcoor)] rotatedz

    sub start()  {

        uword anglex
        uword angley
        uword anglez
        ubyte timer_jiffies

        repeat {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            txt.clear_screenchars(' ')
            draw_edges()
            anglex+=500
            angley+=215
            anglez+=453
        }
    }

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word wcosa = cos8(ax)
        word wsina = sin8(ax)
        word wcosb = cos8(ay)
        word wsinb = sin8(ay)
        word wcosc = cos8(az)
        word wsinc = sin8(az)

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

        ubyte @zp i
        for i in 0 to len(xcoor)-1 {
            ; don't normalize by dividing by 128, instead keep some precision for perspective calc later
            rotatedx[i] = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            rotatedy[i] = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            rotatedz[i] = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
        }
    }

    sub draw_edges() {

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        ubyte @zp i
        word @zp rz
        word @zp persp
        byte sx
        byte sy

        for i in 0 to len(xcoor)-1 {
            rz = rotatedz[i]
            if rz >= 10 {
                persp = 500 + rz/64
                sx = rotatedx[i] / persp as byte + screen_width/2
                sy = rotatedy[i] / persp as byte + screen_height/2
                txt.setcc(sx as ubyte, sy as ubyte, 46, 7)
            }
        }

        for i in 0 to len(xcoor)-1 {
            rz = rotatedz[i]
            if rz < 10 {
                persp = 500 + rz/64
                sx = rotatedx[i] / persp as byte + screen_width/2
                sy = rotatedy[i] / persp as byte + screen_height/2
                txt.setcc(sx as ubyte, sy as ubyte, 81, 7)
            }
        }
    }
}
