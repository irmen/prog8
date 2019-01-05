%import c64utils
%import c64flt

~ main {

    const uword width = 40
    const uword height = 25

    ; vertices
    byte[8] xcoor = [ -50, -50, -50, -50,  50,  50,  50, 50 ]
    byte[8] ycoor = [ -50, -50,  50,  50, -50, -50,  50, 50 ]
    byte[8] zcoor = [ -50,  50, -50,  50, -50,  50, -50, 50 ]

    ; storage for rotated coordinates
    float[len(xcoor)] rotatedx=0.0
    float[len(ycoor)] rotatedy=0.0
    float[len(zcoor)] rotatedz=-1.0

    sub start()  {
        uword anglex
        uword angley
        uword anglez
        while(true) {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            c64.CLEARSCR()
            draw_edges()
            anglex+=1000
            angley+=333
            anglez+=807
        }
    }

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        float cosa = cos8(ax) as float / 128.0
        float sina = sin8(ax) as float / 128.0
        float cosb = cos8(ay) as float / 128.0
        float sinb = sin8(ay) as float / 128.0
        float cosc = cos8(az) as float / 128.0
        float sinc = sin8(az) as float / 128.0

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
            float xc = xcoor[i] as float
            float yc = ycoor[i] as float
            float zc = zcoor[i] as float
            rotatedx[i] = Axx*xc + Axy*yc + Axz*zc
            rotatedy[i] = Ayx*xc + Ayy*yc + Ayz*zc
            rotatedz[i] = Azx*xc + Azy*yc + Azz*zc
        }
    }

    sub draw_edges() {

        sub toscreenx(float x, float z) -> byte {
            return x/(250.0+z) * (height as float) as byte + width // 2
        }

        sub toscreeny(float y, float z) -> byte {
            return y/(250.0+z) * (height as float) as byte + height // 2
        }

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        for ubyte i in 0 to len(xcoor)-1 {
            float rz = rotatedz[i]
            if rz >= 0.1 {
                ubyte sx = toscreenx(rotatedx[i], rz) as ubyte
                ubyte sy = toscreeny(rotatedy[i], rz) as ubyte
                c64scr.setchrclr(sx, sy, 46, i+2)
            }
        }

        for ubyte i in 0 to len(xcoor)-1 {
            float rz = rotatedz[i]
            if rz < 0.1 {
                ubyte sx = toscreenx(rotatedx[i], rz) as ubyte
                ubyte sy = toscreeny(rotatedy[i], rz) as ubyte
                c64scr.setchrclr(sx, sy, 81, i+2)
            }
        }
    }
}
