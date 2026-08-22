%import syslib
%import graphics
%import math

; Note: this program can be compiled for multiple target systems.

main {

    ; vertices
    word[] xcoor = [ -100, -100, -100, -100,  100,  100,  100, 100 ]
    word[] ycoor = [ -100, -100,  100,  100, -100, -100,  100, 100 ]
    word[] zcoor = [ -100,  100, -100,  100, -100,  100, -100, 100 ]

    ; storage for screen coordinates (perspective-corrected)
    word[len(xcoor)] screenx
    word[len(ycoor)] screeny

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
            word rx = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            word ry = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            word rz = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]

            ; perspective projection, done once per vertex
            word persp = 256 + (rz>>8)
            screenx[i] = rx / persp + graphics.WIDTH/2 as uword
            screeny[i] = ry / persp + graphics.HEIGHT/2 as ubyte
        }
    }

    sub draw_lines() {
        ubyte @zp i
        for i in len(edgesFrom) -1 downto 0 {
            ubyte @zp vFrom = edgesFrom[i]
            ubyte @zp vTo = edgesTo[i]
            graphics.line(screenx[vFrom] as uword, screeny[vFrom] as ubyte, screenx[vTo] as uword, screeny[vTo] as ubyte)
        }
    }
}
