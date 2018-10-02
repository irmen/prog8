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

    float[6] xcoor = [-1.0, 1.0, 1.0, 0.5, 0.2, -1.0]
    float[6] ycoor = [0.2, 1.0, -1.0, -0.3, -0.6, -1.0]

    float[len(xcoor)] rotatedx
    float[len(ycoor)] rotatedy

    sub start()  {
        byte i

        while(1) {
            if irq.time_changed {
                irq.time_changed = 0
                _vm_gfx_clearscr(0)
                _vm_gfx_text(120, 40, 5, "Spin to Win !!!")

                for i in 0 to width//10 {
                    _vm_gfx_line(i*2+100, 100, i*10, 199, 6)
                }

                rotate_points(flt(irq.global_time) / 30.0)
                draw_lines()
            }
        }
    }

    sub rotate_points(t: float) {

        ; rotate around origin (0,0) and zoom a bit
        byte i
        float zoom
        zoom = (0.6 + sin(t*1.4)/2.2)

        for i in 0 to len(xcoor)-1 {
            rotatedx[i] = xcoor[i] * cos(t) - ycoor[i] * sin(t)
            rotatedy[i] = xcoor[i] * sin(t) + ycoor[i] * cos(t)
            rotatedx[i] *= zoom
            rotatedy[i] *= zoom
        }
    }


    sub draw_lines() {
        byte i

        sub toscreenx(x: float) -> word {
            return floor(x * height/3 + width /2)
        }

        sub toscreeny(y: float) -> word {
            return floor(y * height/3 + height /2)
        }

        for i in 0 to len(xcoor)-2 {
            _vm_gfx_line(toscreenx(rotatedx[i]), toscreeny(rotatedy[i]),
                         toscreenx(rotatedx[i+1]), toscreeny(rotatedy[i+1]), i+7)
        }
        _vm_gfx_line(toscreenx(rotatedx[len(xcoor)-1]), toscreeny(rotatedy[len(xcoor)-1]),
                     toscreenx(rotatedx[0]), toscreeny(rotatedy[0]), 14)
    }
}
