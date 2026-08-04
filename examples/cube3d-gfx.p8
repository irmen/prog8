%import syslib
%import graphics
%import math

; Note: this program can be compiled for multiple target systems.

main {

    ; vertices
    word[] xcoor = [ -100, -100, -100, -100,  100,  100,  100, 100 ]
    word[] ycoor = [ -100, -100,  100,  100, -100, -100,  100, 100 ]
    word[] zcoor = [ -100,  100, -100,  100, -100,  100, -100, 100 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx
    word[len(ycoor)] rotatedy
    word[len(zcoor)] rotatedz

    ; edges
    ubyte[] edgesFrom = [ 0, 2, 6, 4, 1, 3, 7, 5, 0, 2, 6, 4]
    ubyte[] edgesTo = [ 2, 6, 4, 0, 3, 7, 5, 1, 1, 3, 7, 5]


    sub start()  {
        uword anglex
        uword angley
        uword anglez

        graphics.enable_bitmap_mode()


        repeat {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            graphics.clear_screen(1, 0)
            draw_lines()
            anglex-=500
            angley+=217
            anglez+=452

            ;sys.waitvsync()
            ;sys.waitvsync()
        }
    }

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word wcosa = math.cos8(ax)
        word wsina = math.sin8(ax)
        word wcosb = math.cos8(ay)
        word wsinb = math.sin8(ay)
        word wcosc = math.cos8(az)
        word wsinc = math.sin8(az)

        ; instead of (slow) /128, we simply shift 7 bits (and take the roundoff error)
        word wcosa_sinb = wcosa*wsinb >> 7
        word wsina_sinb = wsina*wsinb >> 7

        word Axx = wcosa*wcosb >> 7
        word Axy = (wcosa_sinb*wsinc - wsina*wcosc) >> 7
        word Axz = (wcosa_sinb*wcosc + wsina*wsinc) >> 7
        word Ayx = wsina*wcosb >> 7
        word Ayy = (wsina_sinb*wsinc + wcosa*wcosc) >> 7
        word Ayz = (wsina_sinb*wcosc - wcosa*wsinc) >> 7
        word Azx = -wsinb
        word Azy = wcosb*wsinc >> 7
        word Azz = wcosb*wcosc >> 7

        ubyte @zp i
        for i in 0 to len(xcoor)-1 {
            ; don't normalize by dividing by 128, instead keep some precision for perspective calc later
            rotatedx[i] = (Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i])
            rotatedy[i] = (Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i])
            rotatedz[i] = (Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i])
        }
    }

    sub draw_lines() {
        ubyte @zp i
        for i in len(edgesFrom) -1 downto 0 {
            ubyte @zp vFrom = edgesFrom[i]
            ubyte @zp vTo = edgesTo[i]
            word @zp persp1 = 256 + (rotatedz[vFrom]>>8)
            word @zp persp2 = 256 + (rotatedz[vTo]>>8)
            graphics.line(rotatedx[vFrom] / persp1 + graphics.WIDTH/2 as uword,
                          rotatedy[vFrom] / persp1 + graphics.HEIGHT/2 as ubyte,
                          rotatedx[vTo] / persp2 + graphics.WIDTH/2 as uword,
                          rotatedy[vTo] / persp2 + graphics.HEIGHT/2 as ubyte)
        }
    }
}
