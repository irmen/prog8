%import c64lib
%import c64utils
%import c64flt

~ main {

    const uword width = 40
    const uword height = 25

    ; vertices
    float[] xcoor = [ -1.0, -1.0, -1.0, -1.0,  1.0,  1.0,  1.0, 1.0 ]
    float[] ycoor = [ -1.0, -1.0,  1.0,  1.0, -1.0, -1.0,  1.0, 1.0 ]
    float[] zcoor = [ -1.0,  1.0, -1.0,  1.0, -1.0,  1.0, -1.0, 1.0 ]

    ; storage for rotated coordinates
    float[len(xcoor)] rotatedx=0.0
    float[len(ycoor)] rotatedy=0.0
    float[len(zcoor)] rotatedz=-1.0

    sub start()  {
        float time=0.0
        while(true) {
            rotate_vertices(time)
            c64scr.clear_screenchars(32)
            draw_edges()
            time+=0.2
            c64scr.plot(0,0)
            c64scr.print("3d cube! (float) ")
            c64scr.print_ub(c64.TIME_LO)
            c64scr.print(" jiffies/frame")
            c64.TIME_LO=0
        }
    }

    sub rotate_vertices(float t) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        float cosa = cos(t)
        float sina = sin(t)
        float cosb = cos(t*0.33)
        float sinb = sin(t*0.33)
        float cosc = cos(t*0.78)
        float sinc = sin(t*0.78)

        float cosa_sinb = cosa*sinb
        float sina_sinb = sina*sinb
        float Axx = cosa*cosb
        float Axy = cosa_sinb*sinc - sina*cosc
        float Axz = cosa_sinb*cosc + sina*sinc
        float Ayx = sina*cosb
        float Ayy = sina_sinb*sinc + cosa*cosc
        float Ayz = sina_sinb*cosc - cosa*sinc
        float Azx = -sinb
        float Azy = cosb*sinc
        float Azz = cosb*cosc

        for ubyte i in 0 to len(xcoor)-1 {
            rotatedx[i] = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            rotatedy[i] = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            rotatedz[i] = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
        }
    }

    sub draw_edges() {

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        for ubyte i in 0 to len(xcoor)-1 {
            float rz = rotatedz[i]
            if rz >= 0.1 {
                float persp = (5.0+rz)/height
                ubyte sx = rotatedx[i] / persp + width/2.0 as ubyte
                ubyte sy = rotatedy[i] / persp + height/2.0 as ubyte
                c64scr.setcc(sx, sy, 46, i+2)
            }
        }

        for ubyte i in 0 to len(xcoor)-1 {
            float rz = rotatedz[i]
            if rz < 0.1 {
                float persp = (5.0+rz)/height
                ubyte sx = rotatedx[i] / persp + width/2.0 as ubyte
                ubyte sy = rotatedy[i] / persp + height/2.0 as ubyte
                c64scr.setcc(sx, sy, 81, i+2)
            }
        }
    }
}
