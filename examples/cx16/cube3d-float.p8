%import cx16flt
%import cx16textio
%zeropage basicsafe

main {

    const uword width = 80
    const uword height = 60

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
        ubyte timer_jiffies

        repeat {
            rotate_vertices(time)
            txt.clear_screenchars(' ')
            draw_edges()
            time+=0.1

            txt.plot(0,0)
            txt.print("3d cube! (floating point calc) ")

            %asm {{
                phx
                jsr  c64.RDTIM      ; A/X/Y
                sta  timer_jiffies
                lda  #0
                jsr  c64.SETTIM
                plx
            }}
            txt.print_ub(timer_jiffies)
            txt.print(" jiffies/fr = ")
            txt.print_ub(60/timer_jiffies)
            txt.print(" fps")
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

        ubyte @zp i
        for i in 0 to len(xcoor)-1 {
            rotatedx[i] = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            rotatedy[i] = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            rotatedz[i] = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
        }
    }

    sub draw_edges() {

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)
        ubyte @zp i
        float rz
        float persp
        ubyte sx
        ubyte sy

        for i in 0 to len(xcoor)-1 {
            rz = rotatedz[i]
            if rz >= 0.1 {
                persp = (5.0+rz)/(height as float)
                sx = rotatedx[i] / persp + width/2.0 as ubyte
                sy = rotatedy[i] / persp + height/2.0 as ubyte
                txt.setcc(sx, sy, 46, 1)
            }
        }

        for i in 0 to len(xcoor)-1 {
            rz = rotatedz[i]
            if rz < 0.1 {
                persp = (5.0+rz)/(height as float)
                sx = rotatedx[i] / persp + width/2.0 as ubyte
                sy = rotatedy[i] / persp + height/2.0 as ubyte
                txt.setcc(sx, sy, 81, 1)
            }
        }
    }
}
