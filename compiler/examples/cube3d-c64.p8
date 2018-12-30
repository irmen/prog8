%import c64utils
%option enable_floats

~ main {

    const uword width = 40
    const uword height = 25

    ; vertices
    float[8] xcoor = [ -1.0, -1.0, -1.0, -1.0,  1.0,  1.0,  1.0, 1.0 ]
    float[8] ycoor = [ -1.0, -1.0,  1.0,  1.0, -1.0, -1.0,  1.0, 1.0 ]
    float[8] zcoor = [ -1.0,  1.0, -1.0,  1.0, -1.0,  1.0, -1.0, 1.0 ]

    ; edges (msb=from vertex, lsb=to vertex)
    uword[12] edges = [$0001, $0103, $0302, $0200, $0405, $0507, $0706, $0604, $0004, $0105, $0206, $0307]

    ; storage for rotated coordinates
    float[len(xcoor)] rotatedx=0.0
    float[len(ycoor)] rotatedy=0.0
    float[len(zcoor)] rotatedz=-1.0

    sub start()  {
        float time=0.0
        while true {
            rotate_vertices(time)
            c64.CLEARSCR()
            draw_edges()
            time += 0.2
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

        float Axx = cosa*cosb
        float Axy = cosa*sinb*sinc - sina*cosc
        float Axz = cosa*sinb*cosc + sina*sinc
        float Ayx = sina*cosb
        float Ayy = sina*sinb*sinc + cosa*cosc
        float Ayz = sina*sinb*cosc - cosa*sinc
        float Azx = -sinb
        float Azy = cosb*sinc
        float Azz = cosb*cosc

        for ubyte i in 0 to len(xcoor)-1 {
            float xc = xcoor[i]
            float yc = ycoor[i]
            float zc = zcoor[i]
            rotatedx[i] = Axx*xc + Axy*yc + Axz*zc
            rotatedy[i] = Ayx*xc + Ayy*yc + Ayz*zc
            rotatedz[i] = Azx*xc + Azy*yc + Azz*zc
        }
    }


    sub draw_edges() {

        sub toscreenx(float x, float z) -> byte {
            return x/(5.0+z) * (height as float) as byte + width // 2
        }

        sub toscreeny(float y, float z) -> byte {
            return y/(5.0+z) * (height as float) as byte + height // 2
        }

        ; plot the points of the 3d cube
        for ubyte i in 0 to len(xcoor)-1 {
            float rz = rotatedz[i]
            ubyte sx = toscreenx(rotatedx[i], rz) as ubyte
            ubyte sy = toscreeny(rotatedy[i], rz) as ubyte
            if rz < 0.1
                c64scr.setchrclr(sx, sy, 81, i+2)
            else
                c64scr.setchrclr(sx, sy, 46, i+2)
        }
    }
}
