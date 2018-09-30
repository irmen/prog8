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

    float x1 = -1.0
    float y1 = 1.0

    float x2 = 1.0
    float y2 = 1.0

    float x3 = 1.0
    float y3 = -1.0

    float x4 = -1.0
    float y4 = -1.0


    float rx1
    float rx2
    float rx3
    float rx4
    float ry1
    float ry2
    float ry3
    float ry4

    sub start()  {
        float t
        _vm_gfx_clearscr(0)

        while(1) {
            if irq.time_changed {
                irq.time_changed = 0
                _vm_gfx_clearscr(0)
                _vm_gfx_text(130, 80, 5, "Spin !!!")
                t = flt(irq.global_time) / 60.0
                rotate_all(t)
                plot_pixels()
            }
        }
    }

    sub rotate_all(t: float) {
        rx1 = x1 * cos(t) - y1 * sin(t)
        ry1 = x1 * sin(t) + y1 * cos(t)

        rx2 = x2 * cos(t) - y2 * sin(t)
        ry2 = x2 * sin(t) + y2 * cos(t)

        rx3 = x3 * cos(t) - y3 * sin(t)
        ry3 = x3 * sin(t) + y3 * cos(t)

        rx4 = x4 * cos(t) - y4 * sin(t)
        ry4 = x4 * sin(t) + y4 * cos(t)
    }


    sub plot_pixels() {
        word sx1
        word sx2
        word sx3
        word sx4
        word sy1
        word sy2
        word sy3
        word sy4

        sx1 = floor(rx1 * height/3 + width/2)
        sx2 = floor(rx2 * height/3 + width/2)
        sx3 = floor(rx3 * height/3 + width/2)
        sx4 = floor(rx4 * height/3 + width/2)
        sy1 = floor(ry1 * height/3 + height/2)
        sy2 = floor(ry2 * height/3 + height/2)
        sy3 = floor(ry3 * height/3 + height/2)
        sy4 = floor(ry4 * height/3 + height/2)

        _vm_gfx_line(sx1, sy1, sx2, sy2, 1)
        _vm_gfx_line(sx2, sy2, sx3, sy3, 7)
        _vm_gfx_line(sx3, sy3, sx4, sy4, 10)
        _vm_gfx_line(sx4, sy4, sx1, sy1, 14)
    }
}
