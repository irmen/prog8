%import syslib
%import conv
%import math
%import monogfx
%import verafx

; TODO add FPS counter
; TODO fix the camera normal calculation for the hidden surface removal
; TODO add all other Elite's ships, show their name, advance to next ship on keypress
; TODO embed pre calculated surface normals???

main {
    sub start()  {
        uword anglex
        uword angley
        uword anglez

        monogfx.lores()
        monogfx.text_charset(1)
        monogfx.clear_screen(false)
        print_ship_name()
        monogfx.enable_doublebuffer()
        monogfx.clear_screen(false)
        print_ship_name()

        repeat {
            matrix_math.rotate_vertices(msb(anglex), msb(angley), msb(anglez))

            ; We use verafx to clear the screen during animation, instead of
            ; the regular routine. This speeds up the frame rate a bit.
            verafx.clear(0, monogfx.buffer_back + 320*16/8, 0, 320/8*220/4)
            ; monogfx.clear_screen(false)

            draw_lines_hiddenremoval()
            ; draw_lines()
            monogfx.swap_buffers(true)

            anglex += 317
            angley -= 505
            anglez += 452
        }
    }

    sub print_ship_name() {
        monogfx.text(20, 0, true, "3d ship model: ")
        monogfx.text(140, 0, true, shipdata.shipName)

        monogfx.text(60, 8, true, conv.str_ub(shipdata.totalNumberOfPoints))
        monogfx.text(80, 8, true, "vertices,")

        monogfx.text(160, 8, true, conv.str_ub(shipdata.totalNumberOfEdges))
        monogfx.text(180, 8, true, "edges,")

        monogfx.text(240, 8, true, conv.str_ub(shipdata.totalNumberOfFaces))
        monogfx.text(260, 8, true, "faces")
    }


    const uword screen_width = 320
    const ubyte screen_height = 240

    sub draw_lines() {
        ; simple routine that draw all edges, exactly once, but no hidden line removal.
        ubyte @zp i
        for i in shipdata.totalNumberOfEdges -1 downto 0 {
            ubyte @zp vFrom = shipdata.edgesFrom[i]
            ubyte @zp vTo = shipdata.edgesTo[i]
            word persp1 = 200 + matrix_math.rotatedz[vFrom]/256
            word persp2 = 200 + matrix_math.rotatedz[vTo]/256
            monogfx.line(matrix_math.rotatedx[vFrom] / persp1 + screen_width/2 as uword,
                matrix_math.rotatedy[vFrom] / persp1 + screen_height/2 as uword,
                matrix_math.rotatedx[vTo] / persp2 + screen_width/2 as uword,
                matrix_math.rotatedy[vTo] / persp2 + screen_height/2 as uword,
                true)
        }
    }

    sub draw_lines_hiddenremoval() {
        ; complex drawing routine that draws the ship model based on its faces,
        ; where it uses the surface normals to determine visibility.
        sys.memset(edgestodraw, shipdata.totalNumberOfEdges, 1)
        ubyte @zp edgeIdx = 0
        ubyte @zp pointIdx = 0
        ubyte faceNumber
        for faceNumber in shipdata.totalNumberOfFaces -1 downto 0 {
            if matrix_math.facing_away(pointIdx) {
                ; don't draw this face, fast-forward over the edges and points
                edgeIdx += 3    ; every face hast at least 3 edges
                while shipdata.facesEdges[edgeIdx]!=255 {
                    edgeIdx++
                }
                edgeIdx++
                pointIdx += 3    ; every face has at least 3 points
                while shipdata.facesPoints[pointIdx]!=255 {
                    pointIdx++
                }
                pointIdx++
            } else {
                ; draw this face
                ubyte @zp e1 = shipdata.facesEdges[edgeIdx]
                edgeIdx ++
                ubyte @zp e2 = shipdata.facesEdges[edgeIdx]
                edgeIdx ++
                ubyte @zp e3 = shipdata.facesEdges[edgeIdx]
                edgeIdx ++
                if edgestodraw[e1]
                    draw_edge(e1)
                if edgestodraw[e2]
                    draw_edge(e2)
                while e3!=255 {
                    if edgestodraw[e3]
                        draw_edge(e3)
                    e3 = shipdata.facesEdges[edgeIdx]
                    edgeIdx ++
                }
                ; skip the rest of the facesPoints, we don't need them here anymore
                pointIdx += 3    ; every face has at least 3 points
                while shipdata.facesPoints[pointIdx]!=255 {
                    pointIdx++
                }
                pointIdx++
            }
        }
    }

    bool[shipdata.totalNumberOfEdges] edgestodraw

    sub draw_edge(ubyte edgeidx) {
        edgestodraw[edgeidx] = false
        ubyte vFrom = shipdata.edgesFrom[edgeidx]
        ubyte vTo = shipdata.edgesTo[edgeidx]
        word persp1 = 170 + matrix_math.rotatedz[vFrom]/256
        word persp2 = 170 + matrix_math.rotatedz[vTo]/256
        monogfx.line(matrix_math.rotatedx[vFrom] / persp1 + screen_width/2 as uword,
            matrix_math.rotatedy[vFrom] / persp1 + screen_height/2 as uword,
            matrix_math.rotatedx[vTo] / persp2 + screen_width/2 as uword,
            matrix_math.rotatedy[vTo] / persp2 + screen_height/2 as uword,
            true)
    }
}

matrix_math {
    %option verafxmuls      ; accellerate all word-multiplications in this block using Vera FX hardware muls

    ; storage for rotated coordinates
    word[shipdata.totalNumberOfPoints] rotatedx
    word[shipdata.totalNumberOfPoints] rotatedy
    word[shipdata.totalNumberOfPoints] rotatedz

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
        for i in 0 to shipdata.totalNumberOfPoints-1 {
            ; don't normalize by dividing by 128, instead keep some precision for perspective calc later
            rotatedx[i] = Axx*shipdata.xcoor[i] + Axy*shipdata.ycoor[i] + Axz*shipdata.zcoor[i]
            rotatedy[i] = Ayx*shipdata.xcoor[i] + Ayy*shipdata.ycoor[i] + Ayz*shipdata.zcoor[i]
            rotatedz[i] = Azx*shipdata.xcoor[i] + Azy*shipdata.ycoor[i] + Azz*shipdata.zcoor[i]
        }
    }

    sub facing_away(ubyte edgePointsIdx) -> bool {
        ; simplistic visibility determination by checking the Z component of the surface normal
        ; TODO: actually take the line of sight vector into account
        ubyte p1 = shipdata.facesPoints[edgePointsIdx]
        edgePointsIdx++
        ubyte p2 = shipdata.facesPoints[edgePointsIdx]
        edgePointsIdx++
        ubyte p3 = shipdata.facesPoints[edgePointsIdx]
        word p1x = rotatedx[p1] / 128
        word p1y = rotatedy[p1] / 128
        word p2x = rotatedx[p2] / 128
        word p2y = rotatedy[p2] / 128
        word p3x = rotatedx[p3] / 128
        word p3y = rotatedy[p3] / 128
        return (p2x-p3x)*(p1y-p3y) - (p2y-p3y)*(p1x-p3x) > 0
    }
}

shipdata {

        ; Ship model data converted from BBC Elite's Cobra MK 3
    ; downloaded from http://www.elitehomepage.org/archive/index.htm

const ubyte totalNumberOfEdges = 51
const ubyte totalNumberOfFaces = 22
const ubyte totalNumberOfPoints = 34
str shipName = "cobra-mk3"
; vertices
word[totalNumberOfPoints] xcoor = [ 32,-32,0,-120,120,-88,88,128,-128,0,-32,32,-36,-8,8,36,36,8,-8,-36,-1,-1,-80,-80,-88,80,88,80,1,1,1,1,-1,-1 ]
word[totalNumberOfPoints] ycoor = [ 0,0,26,-3,-3,16,16,-8,-8,26,-24,-24,8,12,12,8,-12,-16,-16,-12,-1,-1,-6,6,0,6,0,-6,-1,-1,1,1,1,1 ]
word[totalNumberOfPoints] zcoor = [ 76,76,24,-8,-8,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,-40,76,90,-40,-40,-40,-40,-40,-40,76,90,76,90,76,90 ]
; edges and faces
ubyte[totalNumberOfEdges] edgesFrom = [ 0,1,0,10,1,0,2,0,4,0,4,7,2,1,1,3,8,3,2,5,6,5,6,16,15,14,14,18,13,12,12,26,25,25,22,23,22,20,28,21,20,28,29,30,31,30,32,20,21,20,20 ]
ubyte[totalNumberOfEdges] edgesTo = [ 1,2,2,11,10,11,6,6,6,4,7,11,5,5,3,5,10,8,9,9,9,8,7,17,16,15,17,19,18,13,19,27,26,27,23,24,24,28,29,29,21,30,31,31,33,32,33,32,33,33,29 ]
ubyte[] facesPoints = [
     0,1,2 ,255,
     11,10,1,0 ,255,
     0,2,6 ,255,
     6,4,0 ,255,
     4,7,11,0 ,255,
     5,2,1 ,255,
     1,3,5 ,255,
     10,8,3,1 ,255,
     9,2,5 ,255,
     9,6,2 ,255,
     3,8,5 ,255,
     4,6,7 ,255,
     5,8,10,11,7,6,9 ,255,
     17,16,15,14 ,255,
     19,18,13,12 ,255,
     27,26,25 ,255,
     22,23,24 ,255,
     20,28,29,21 ,255,
     30,28,29,31 ,255,
     33,31,30,32 ,255,
     20,32,33,21 ,255,
     29,31,33,20 ,255
]
ubyte[] facesEdges = [
     0,1,2 ,255,
     3,4,0,5 ,255,
     2,6,7 ,255,
     8,9,7 ,255,
     10,11,5,9 ,255,
     12,1,13 ,255,
     14,15,13 ,255,
     16,17,14,4 ,255,
     18,12,19 ,255,
     20,6,18 ,255,
     17,21,15 ,255,
     8,22,10 ,255,
     21,16,3,11,22,20,19 ,255,
     23,24,25,26 ,255,
     27,28,29,30 ,255,
     31,32,33 ,255,
     34,35,36 ,255,
     37,38,39,40 ,255,
     41,38,42,43 ,255,
     44,43,45,46 ,255,
     47,46,48,40 ,255,
     42,44,49,50 ,255
]

}
