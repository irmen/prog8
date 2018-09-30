%option enable_floats

~ main {

    sub start()  {

        _vm_gfx_clearscr(0)
        _vm_gfx_text(5, 5, 7, "Swirl !!!")

        const word width = 320
        const word height = 200
        float x
        float y
        float t
        byte color

        while(1) {
            x = ((sin(t*1.01) +cos(t*1.1234)) * width/4.1) + width/2
            y = ((cos(t)+sin(t*0.03456)) * height/4.1) + height/2
            _vm_gfx_pixel(floor(x),floor(y), color//16)
            t  += 0.01
            color++
        }
    }
}
