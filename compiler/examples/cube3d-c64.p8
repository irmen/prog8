%import c64utils
%option enable_floats

~ irq {
    uword global_time
    ubyte time_changed

    sub irq() {
        global_time++
        time_changed = 1
    }
}


~ main {

    const uword width = 320
    const uword height = 200

    ; vertices
    float[8] xcoor = [ -1.0, -1.0, -1.0, -1.0,  1.0,  1.0,  1.0, 1.0 ]
    float[8] ycoor = [ -1.0, -1.0,  1.0,  1.0, -1.0, -1.0,  1.0, 1.0 ]
    float[8] zcoor = [ -1.0,  1.0, -1.0,  1.0, -1.0,  1.0, -1.0, 1.0 ]

    ; edges (msb=from vertex, lsb=to vertex)
    uword[12] edges = [$0001, $0103, $0302, $0200, $0405, $0507, $0706, $0604, $0004, $0105, $0206, $0307]

    ; storage for rotated coordinates
    float[len(xcoor)] rotatedx
    float[len(ycoor)] rotatedy
    float[len(zcoor)] rotatedz

    sub start()  {
        while true {
            if irq.time_changed {
                irq.time_changed = 0
                ;vm_gfx_clearscr(0)
                ;vm_gfx_text(8, 6, 1, "Spin")
                ;vm_gfx_text(29, 11, 1, "to Win !")

                for uword i in 0 to width//10 {
                    uword x=i*2+width//2-width//10
                    ;vm_gfx_line(x, 130, i*10.w, 199, 6)
                }

                rotate_vertices(irq.global_time as float / 30.0)
                c64scr.print_ub(X)
                c64.CHROUT('\n')
                draw_edges()
                c64scr.print_ub(X)
                c64.CHROUT('\n')
            }
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
            rotatedx[i] = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            rotatedy[i] = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            rotatedz[i] = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
        }
    }


    sub draw_edges() {

        sub toscreenx(float x, float z) -> word {
            return x/(4.2+z) * (height as float) as word + width // 2
        }

        sub toscreeny(float y, float z) -> word {
            return y/(4.2+z) * (height as float) as word + height // 2
        }

        ; draw all edges of the object
        for uword edge in edges {

            ubyte e_from = msb(edge)
            ubyte e_to = lsb(edge)

            word x1 = toscreenx(rotatedx[e_from], rotatedz[e_from])
            word y1 = toscreeny(rotatedy[e_from], rotatedz[e_from])
            word x2 = toscreenx(rotatedx[e_to], rotatedz[e_to])
            word y2 = toscreeny(rotatedy[e_to], rotatedz[e_to])
            ubyte color = e_from+e_to
            ;vm_gfx_line(x1, y1, x2, y2)
        }

        ; accentuate the vertices a bit with small boxes
        for ubyte i in 0 to len(xcoor)-1 {
            word sx = toscreenx(rotatedx[i], rotatedz[i])
            word sy = toscreeny(rotatedy[i], rotatedz[i])
            ubyte color=i+2
;            vm_gfx_pixel(sx-1, sy-1, color)
;            vm_gfx_pixel(sx, sy-1, color)
;            vm_gfx_pixel(sx+1, sy-1, color)
;            vm_gfx_pixel(sx-1, sy, color)
;            vm_gfx_pixel(sx, sy, color)
;            vm_gfx_pixel(sx+1, sy, color)
;            vm_gfx_pixel(sx-1, sy+1, color)
;            vm_gfx_pixel(sx, sy+1, color)
;            vm_gfx_pixel(sx+1, sy+1, color)
;            vm_gfx_pixel(sx, sy-2, color)
;            vm_gfx_pixel(sx+2, sy, color)
;            vm_gfx_pixel(sx, sy+2, color)
;            vm_gfx_pixel(sx-2, sy, color)
        }
    }
}
