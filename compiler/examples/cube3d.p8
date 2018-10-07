%option enable_floats

~ irq {
    word global_time
    byte time_changed

    sub irq() {
        global_time++
        time_changed = 1
    }
}


~ main {

    const word width = 320
    const word height = 200

    ; vertices
    float[8] xcoor = [ -1.0, -1.0, -1.0, -1.0,  1.0,  1.0,  1.0, 1.0 ]
    float[8] ycoor = [ -1.0, -1.0,  1.0,  1.0, -1.0, -1.0,  1.0, 1.0 ]
    float[8] zcoor = [ -1.0,  1.0, -1.0,  1.0, -1.0,  1.0, -1.0, 1.0 ]

    ; edges (msb=from vertex, lsb=to vertex)
    word[12] edges = [$0001, $0103, $0302, $0200, $0405, $0507, $0706, $0604, $0004, $0105, $0206, $0307]

    ; storage for rotated coordinates
    float[len(xcoor)] rotatedx
    float[len(ycoor)] rotatedy
    float[len(zcoor)] rotatedz

    sub start()  {
        while(1) {
            if irq.time_changed {
                irq.time_changed = 0
                _vm_gfx_clearscr(0)
                _vm_gfx_text(8, 6, 1, "Spin")
                _vm_gfx_text(29, 11, 1, "to Win !")

                for byte i in 0 to width//10 {
                    _vm_gfx_line(i*2+width//2-width//10, 130, i*10.w, 199, 6)
                }

                rotate_vertices(flt(irq.global_time) / 30.0)
                draw_edges()
            }
        }
    }

    sub rotate_vertices(t: float) {
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

        for byte i in 0 to len(xcoor)-1 {
            rotatedx[i] = Axx*xcoor[i] + Axy*ycoor[i] + Axz*zcoor[i]
            rotatedy[i] = Ayx*xcoor[i] + Ayy*ycoor[i] + Ayz*zcoor[i]
            rotatedz[i] = Azx*xcoor[i] + Azy*ycoor[i] + Azz*zcoor[i]
        }
    }


    sub draw_edges() {

        sub toscreenx(x: float, z: float) -> word {
            return floor(x/(4.2+z) * flt(height)) + width // 2
        }

        sub toscreeny(y: float, z: float) -> word {
            return floor(y/(4.2+z) * flt(height)) + height // 2
        }

        ; draw all edges of the object
        for word edge in edges {
            byte e_from = msb(edge)
            byte e_to = lsb(edge)
            _vm_gfx_line(toscreenx(rotatedx[e_from], rotatedz[e_from]), toscreeny(rotatedy[e_from], rotatedz[e_from]),
                         toscreenx(rotatedx[e_to], rotatedz[e_to]), toscreeny(rotatedy[e_to], rotatedz[e_to]), e_from+e_to)
        }

        ; accentuate the vertices a bit with small boxes
        for byte i in 0 to len(xcoor)-1 {
            word sx = toscreenx(rotatedx[i], rotatedz[i])
            word sy = toscreeny(rotatedy[i], rotatedz[i])
            byte color=i+2
            _vm_gfx_pixel(sx-1, sy-1, color)
            _vm_gfx_pixel(sx, sy-1, color)
            _vm_gfx_pixel(sx+1, sy-1, color)
            _vm_gfx_pixel(sx-1, sy, color)
            _vm_gfx_pixel(sx, sy, color)
            _vm_gfx_pixel(sx+1, sy, color)
            _vm_gfx_pixel(sx-1, sy+1, color)
            _vm_gfx_pixel(sx, sy+1, color)
            _vm_gfx_pixel(sx+1, sy+1, color)
            _vm_gfx_pixel(sx, sy-2, color)
            _vm_gfx_pixel(sx+2, sy, color)
            _vm_gfx_pixel(sx, sy+2, color)
            _vm_gfx_pixel(sx-2, sy, color)
        }
    }
}
