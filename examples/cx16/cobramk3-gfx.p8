%target cx16
%import cx16lib

main {

    ; Ship model data converted from BBC Elite's Cobra MK 3
    ; downloaded from http://www.elitehomepage.org/archive/index.htm

    ; vertices
    word[] xcoor = [ 32,-32,0,-120,120,-88,88,128,-128,0,-32,32,-36,-8,8,36,36,8,-8,-36,-1,-1,-80,-80,-88,80,88,80,1,1,1,1,-1,-1 ]
    word[] ycoor = [ 0,0,26,-3,-3,16,16,-8,-8,26,-24,-24,8,12,12,8,-12,-16,-16,-12,-1,-1,-6,6,0,6,0,-6,-1,-1,1,1,1,1 ]
    word[] zcoor = [ 76,76,24,-8,-8,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,76,90,-40,-40,-40,-40,-40,-40,76,90,76,90,76,90 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx
    word[len(ycoor)] rotatedy
    word[len(zcoor)] rotatedz

    ; edges
    ubyte[] edgesFrom = [ 0,1,0,10,1,0,2,0,4,0,4,7,2,1,1,3,8,3,2,5,6,5,6,16,15,14,14,18,13,12,12,26,25,25,22,23,22,20,28,21,20,28,29,30,31,30,32,20,21,20,20 ]
    ubyte[] edgesTo = [ 1,2,2,11,10,11,6,6,6,4,7,11,5,5,3,5,10,8,9,9,9,8,7,17,16,15,17,19,18,13,19,27,26,27,23,24,24,28,29,29,21,30,31,31,33,32,33,32,33,33,29 ]

    ; TODO hidden surface removal - this needs surfaces data and surface normal calculations.
    ; TODO add all other Elite's ships, show their name, advance to next ship on keypress

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
            anglex-=500
            angley+=217
            anglez+=452
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

    const uword screen_width = 320
    const ubyte screen_height = 200

    sub draw_lines() {
        ubyte @zp i
        for i in len(edgesFrom) -1 downto 0 {
            ubyte @zp vFrom = edgesFrom[i]
            ubyte @zp vTo = edgesTo[i]
            word persp1 = 200 + rotatedz[vFrom]/256
            word persp2 = 200 + rotatedz[vTo]/256
            cx16.r0 = rotatedx[vFrom] / persp1 + screen_width/2 as uword
            cx16.r1 = rotatedy[vFrom] / persp1 + screen_height/2 as uword
            cx16.r2 = rotatedx[vTo] / persp2 + screen_width/2 as uword
            cx16.r3 = rotatedy[vTo] / persp2 + screen_height/2 as uword
            cx16.GRAPH_draw_line()
        }
    }
}
