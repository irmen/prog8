%import cx16lib

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

        void cx16.screen_set_mode($80)
        cx16.r0 = 0
        cx16.GRAPH_init()
        cx16.GRAPH_set_colors(1, 2, 0)

        repeat {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            cx16.GRAPH_clear()
            draw_lines()
            anglex-=250
            angley+=109
            anglez+=226
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
            rotatedx[i] = (Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i])
            rotatedy[i] = (Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i])
            rotatedz[i] = (Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i])
        }
    }

    const uword screen_width = 320
    const ubyte screen_height = 200

    sub draw_lines() {
        ubyte @zp i
        for i in len(edgesFrom) -1 downto 0 {
            ubyte @zp vFrom = edgesFrom[i]
            ubyte @zp vTo = edgesTo[i]
            word persp1 = 256 + rotatedz[vFrom]/256
            word persp2 = 256 + rotatedz[vTo]/256
            cx16.r0 = rotatedx[vFrom] / persp1 + screen_width/2 as uword
            cx16.r1 = rotatedy[vFrom] / persp1 + screen_height/2 as uword
            cx16.r2 = rotatedx[vTo] / persp2 + screen_width/2 as uword
            cx16.r3 = rotatedy[vTo] / persp2 + screen_height/2 as uword
            cx16.GRAPH_draw_line()      ; TODO are there bugs in here?  the lines are all wrong...
        }
    }
}
