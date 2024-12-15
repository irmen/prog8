%import textio
%import math

main {

    sub start()  {

        uword anglex
        uword angley
        uword anglez

        repeat {
            matrix_math.rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            txt.clear_screenchars(' ')
            draw_edges()
            anglex+=500
            angley+=215
            anglez+=453
            sys.waitvsync()
            sys.waitvsync()
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

        for i in 0 to len(matrix_math.xcoor)-1 {
            rz = matrix_math.rotatedz[i]
            if rz >= 10 {
                persp = 400 + rz/64
                sx = matrix_math.rotatedx[i] / persp as byte + txt.DEFAULT_WIDTH/2
                sy = matrix_math.rotatedy[i] / persp as byte + txt.DEFAULT_HEIGHT/2
                txt.setcc(sx as ubyte, sy as ubyte, 46, 7)
            }
        }

        for i in 0 to len(matrix_math.xcoor)-1 {
            rz = matrix_math.rotatedz[i]
            if rz < 10 {
                persp = 400 + rz/64
                sx = matrix_math.rotatedx[i] / persp as byte + txt.DEFAULT_WIDTH/2
                sy = matrix_math.rotatedy[i] / persp as byte + txt.DEFAULT_HEIGHT/2
                txt.setcc(sx as ubyte, sy as ubyte, 81, 7)
            }
        }
    }
}

matrix_math {
    %option verafxmuls      ; accellerate all word-multiplications in this block using Vera FX hardware muls

    ; vertices
    word[] xcoor = [ -40, -40, -40, -40,  40,  40,  40, 40 ]
    word[] ycoor = [ -40, -40,  40,  40, -40, -40,  40, 40 ]
    word[] zcoor = [ -40,  40, -40,  40, -40,  40, -40, 40 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx
    word[len(ycoor)] rotatedy
    word[len(zcoor)] rotatedz

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word wcosa = math.cos8(ax)
        word wsina = math.sin8(ax)
        word wcosb = math.cos8(ay)
        word wsinb = math.sin8(ay)
        word wcosc = math.cos8(az)
        word wsinc = math.sin8(az)

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

}
