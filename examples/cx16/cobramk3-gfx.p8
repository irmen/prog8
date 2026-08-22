%import syslib
%import conv
%import math
%import monogfx
%import verafx
%import floats

; TODO add all other Elite's ships, show their name, advance to next ship on keypress

main {
    sub start()  {
        uword anglex
        uword angley
        uword anglez
        uword last_time
        uword frame_count
        uword fps

        monogfx.lores()
        monogfx.text_charset(1)
        monogfx.clear_screen(false)
        print_ship_name()
        monogfx.enable_doublebuffer()
        last_time = cbm.RDTIM16()
        frame_count = 0
        fps = 0
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

            ; FPS counter - updated every second
            frame_count++
            uword current_time = cbm.RDTIM16()
            uword elapsed = current_time - last_time
            if elapsed >= 60 {
                fps = frame_count
                frame_count = 0
                last_time = current_time
                ; clear previous FPS text area and draw new value
                monogfx.fillrect(268, 0, 52, 8, false)
                monogfx.text(268, 0, true, "fps:")
                monogfx.text(298, 0, true, conv.str_uw(fps))
            }

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
            monogfx.line(matrix_math.screenx[vFrom] as uword,
                matrix_math.screeny[vFrom] as uword,
                matrix_math.screenx[vTo] as uword,
                matrix_math.screeny[vTo] as uword,
                true)
        }
    }

    sub draw_lines_hiddenremoval() {
        ; determine visibility of each face
        ubyte faceNumber
        for faceNumber in 0 to matrix_math.FACE_COUNT-1 {
            matrix_math.face_visible[faceNumber] = not matrix_math.facing_away_fast_but_imprecise(matrix_math.facePointIdx[faceNumber])
        }

        ; draw every edge that belongs to at least one visible face
        ubyte @zp edgeIdx
        for edgeIdx in 0 to shipdata.totalNumberOfEdges-1 {
            if matrix_math.face_visible[matrix_math.edgeFaceA[edgeIdx]] or
                    (matrix_math.edgeFaceB[edgeIdx] != 255 and matrix_math.face_visible[matrix_math.edgeFaceB[edgeIdx]]) {
                draw_edge(edgeIdx)
            }
        }
    }

    sub draw_edge(ubyte edgeidx) {
        ubyte vFrom = shipdata.edgesFrom[edgeidx]
        ubyte vTo = shipdata.edgesTo[edgeidx]
        monogfx.line(matrix_math.screenx[vFrom] as uword,
            matrix_math.screeny[vFrom] as uword,
            matrix_math.screenx[vTo] as uword,
            matrix_math.screeny[vTo] as uword,
            true)
    }
}

matrix_math {
    %option verafxmuls      ; accelerate all word-multiplications in this block using Vera FX hardware muls

    ; storage for rotated coordinates
    word[shipdata.totalNumberOfPoints] rotatedx
    word[shipdata.totalNumberOfPoints] rotatedy
    word[shipdata.totalNumberOfPoints] rotatedz
    ; precomputed screen coordinates (perspective-corrected) for drawing
    word[shipdata.totalNumberOfPoints] screenx
    word[shipdata.totalNumberOfPoints] screeny

    const ubyte FACE_COUNT = 22
    ; start index of each face in shipdata.facesPoints
    ubyte[FACE_COUNT] facePointIdx = [ 0, 4, 9, 13, 17, 22, 26, 30, 35, 39, 43, 47, 51, 59, 64, 69, 73, 77, 82, 87, 92, 97 ]
    ; for each edge, the one or two incident faces (255 means no second face)
    ubyte[shipdata.totalNumberOfEdges] edgeFaceA = [ 0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 10, 11, 13, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 16, 16, 16, 17, 17, 17, 17, 18, 18, 18, 19, 19, 19, 20, 20, 21, 21 ]
    ubyte[shipdata.totalNumberOfEdges] edgeFaceB = [ 1, 5, 2, 12, 7, 4, 9, 3, 11, 4, 11, 12, 8, 6, 7, 10, 12, 10, 9, 12, 12, 12, 12, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 18, 255, 20, 255, 21, 19, 21, 255, 20, 255, 255, 255, 255 ]
    bool[FACE_COUNT] face_visible

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word wcosa = math.cos8(ax)
        word wsina = math.sin8(ax)
        word wcosb = math.cos8(ay)
        word wsinb = math.sin8(ay)
        word wcosc = math.cos8(az)
        word wsinc = math.sin8(az)

        word wcosa_sinb = wcosa*wsinb  >> 7
        word wsina_sinb = wsina*wsinb  >> 7

        word Axx = wcosa*wcosb  >> 7
        word Axy = (wcosa_sinb*wsinc - wsina*wcosc)  >> 7
        word Axz = (wcosa_sinb*wcosc + wsina*wsinc)  >> 7
        word Ayx = wsina*wcosb  >> 7
        word Ayy = (wsina_sinb*wsinc + wcosa*wcosc)  >> 7
        word Ayz = (wsina_sinb*wcosc - wcosa*wsinc)  >> 7
        word Azx = -wsinb
        word Azy = wcosb*wsinc  >> 7
        word Azz = wcosb*wcosc  >> 7

        ubyte @zp i
        for i in 0 to shipdata.totalNumberOfPoints-1 {
            ; don't normalize by dividing by 128, instead keep some precision for perspective calc later
            rotatedx[i] = Axx*shipdata.xcoor[i] + Axy*shipdata.ycoor[i] + Axz*shipdata.zcoor[i]
            rotatedy[i] = Ayx*shipdata.xcoor[i] + Ayy*shipdata.ycoor[i] + Ayz*shipdata.zcoor[i]
            rotatedz[i] = Azx*shipdata.xcoor[i] + Azy*shipdata.ycoor[i] + Azz*shipdata.zcoor[i]

            ; perspective projection, done once per vertex instead of once per edge
            word persp = 170 + rotatedz[i]/256
            if persp < 32
                persp = 32
            screenx[i] = rotatedx[i] / persp + 160 as uword
            screeny[i] = rotatedy[i] / persp + 120 as uword
        }
    }

    sub facing_away_fast_but_imprecise(ubyte edgePointsIdx) -> bool {
        ; simplistic visibility determination by checking the Z component of the surface normal
        ; this only compares the surface normal to the screen space vector which doesn't yield the proper perspective correct result, but is fast
        ubyte p1 = shipdata.facesPoints[edgePointsIdx]
        edgePointsIdx++
        ubyte p2 = shipdata.facesPoints[edgePointsIdx]
        edgePointsIdx++
        ubyte p3 = shipdata.facesPoints[edgePointsIdx]

        word p1x = rotatedx[p1] >> 7
        word p1y = rotatedy[p1] >> 7
        word p2x = rotatedx[p2] >> 7
        word p2y = rotatedy[p2] >> 7
        word p3x = rotatedx[p3] >> 7
        word p3y = rotatedy[p3] >> 7
        return (p2x-p3x)*(p1y-p3y) - (p2y-p3y)*(p1x-p3x) > 0
    }

    sub facing_away_slow_but_precise(ubyte edgePointsIdx) -> bool {
        ; determine visibility by calculating the dot product of surface normal and view vector
        ubyte p1 = shipdata.facesPoints[edgePointsIdx]
        edgePointsIdx++
        ubyte p2 = shipdata.facesPoints[edgePointsIdx]
        edgePointsIdx++
        ubyte p3 = shipdata.facesPoints[edgePointsIdx]

        ; Calculate two edge vectors of the triangle  (scaled by 2)
        word v1x = (rotatedx[p2] - rotatedx[p1]) >> 7
        word v1y = (rotatedy[p2] - rotatedy[p1]) >> 7
        word v1z = (rotatedz[p2] - rotatedz[p1]) >> 7
        word v2x = (rotatedx[p3] - rotatedx[p1]) >> 7
        word v2y = (rotatedy[p3] - rotatedy[p1]) >> 7
        word v2z = (rotatedz[p3] - rotatedz[p1]) >> 7

        ; Calculate surface normal using cross product: N = V1 x V2     (scaled by 4)
        ; Note: because of lack of precision in the 16 bit word math, we need to use floating point math here.... :-(
        ; Elite had a more optimized version of this algorithm that still used fixed point integer math only...
        float normalx = (v1y * v2z - v1z * v2y) as float
        float normaly = (v1z * v2x - v1x * v2z) as float
        float normalz = (v1x * v2y - v1y * v2x) as float

        ; Calculate view vector from camera (0,0,-170) to point p1   (scaled by 4)
        float viewx = rotatedx[p1]/(256/4) - 0          as float        ; from camera x to point x
        float viewy = rotatedy[p1]/(256/4) - 0          as float        ; from camera y to point y
        float viewz = rotatedz[p1]/(256/4) - (-170*4)   as float        ; from camera z to point z

        ; Calculate dot product of normal and view vector
        ; If dot product is negative, the face is pointing away from the camera
        return normalx * viewx + normaly * viewy + normalz * viewz < 0
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
