%import c64utils
%option enable_floats

~ main {

    const uword width = 40
    const uword height = 25

    ; vertices
    float[8] xcoor = [ -1.0, -1.0, -1.0, -1.0,  1.0,  1.0,  1.0, 2.0 ]
    float[8] ycoor = [ -1.0, -1.0,  1.0,  1.0, -1.0, -1.0,  1.0, 3.0 ]
    float[8] zcoor = [ -1.0,  1.0, -1.0,  1.0, -1.0,  1.0, -1.0, 4.0 ]

    ; edges (msb=from vertex, lsb=to vertex)
    uword[12] edges = [$0001, $0103, $0302, $0200, $0405, $0507, $0706, $0604, $0004, $0105, $0206, $0307]

    ; storage for rotated coordinates
    float[len(xcoor)] rotatedx
    float[len(ycoor)] rotatedy
    float[len(zcoor)] rotatedz

    sub start()  {
        float time=0.0
        while true {
            c64scr.print("stack1 ")
            c64scr.print_ub(X)
            c64.CHROUT('\n')
            rotate_vertices(time)
;            c64scr.print("stack2 ")
;            c64scr.print_ub(X)
;            c64.CHROUT('\n')
;            draw_edges()
            c64scr.print("stack3 ")
            c64scr.print_ub(X)
            c64.CHROUT('\n')
            time += 0.1
        }
    }

    sub rotate_vertices(float t) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
;        float cosa = cos(t)
;        float sina = sin(t)
;        float cosb = cos(t*0.33)
;        float sinb = sin(t*0.33)
;        float cosc = cos(t*0.78)
;        float sinc = sin(t*0.78)
;
;        float Axx = cosa*cosb
;        float Axy = cosa*sinb*sinc - sina*cosc
;        float Axz = cosa*sinb*cosc + sina*sinc
;        float Ayx = sina*cosb
;        float Ayy = sina*sinb*sinc + cosa*cosc
;        float Ayz = sina*sinb*cosc - cosa*sinc
;        float Azx = -sinb
;        float Azy = cosb*sinc
;        float Azz = cosb*cosc

        for ubyte i in 0 to len(xcoor)-1 {
            float xc = xcoor[i]
            c64scr.print("i=")
            c64scr.print_ub(i)
            c64scr.print("  xc=")
            c64flt.print_f(xc)
            c64.CHROUT('\n')
            float yc = ycoor[i]
            c64scr.print("i=")
            c64scr.print_ub(i)
            c64scr.print("  yc=")
            c64flt.print_f(yc)
            c64.CHROUT('\n')
            float zc = zcoor[i]
            c64scr.print("i=")
            c64scr.print_ub(i)
            c64scr.print("  zc=")
            c64flt.print_f(zc)
            c64.CHROUT('\n')
            %breakpoint

; @todo the calculations below destroy the  contents of the coor[] arrays???
;            float rx=Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
;            float ry=Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
;            float rz=Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
;            c64scr.print(" rx=")
;            c64flt.print_f(rx)
;            c64.CHROUT('\n')
;            c64scr.print(" ry=")
;            c64flt.print_f(rx)
;            c64.CHROUT('\n')
;            c64scr.print(" rz=")
;            c64flt.print_f(rx)
;            c64.CHROUT('\n')


            ;rotatedx[i] = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            ;rotatedy[i] = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            ;rotatedz[i] = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
        }
    }


    sub draw_edges() {

        sub toscreenx(float x, float z) -> byte {
            ;return x/(4.2+z) * (height as float) as byte + width // 2
            c64flt.print_f(x)
            c64.CHROUT('\n')
            float fx = (x*8.0 + 20.0)
            return fx as byte

        }

        sub toscreeny(float y, float z) -> byte {
            ;return y/(4.2+z) * (height as float) as byte + height // 2
            c64flt.print_f(y)
            c64.CHROUT('\n')
            float fy = (y*8.0 + 12.0)
            return fy as byte
        }

        ; plot the points of the 3d cube
        for ubyte i in 0 to len(xcoor)-1 {
            ubyte sx = toscreenx(rotatedx[i], rotatedz[i]) as ubyte
            ubyte sy = toscreeny(rotatedy[i], rotatedz[i]) as ubyte
            c64scr.setchrclr(sx, sy, 81, i+2)
        }
    }
}
