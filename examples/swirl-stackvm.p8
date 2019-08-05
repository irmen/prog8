%output raw
%launcher none
%import c64flt

main {

    const uword width = 320
    const uword height = 200

    sub start()  {

        vm_gfx_clearscr(0)

        float t
        ubyte color

        while true {
            float x = sin(t*1.01) + cos(t*1.1234)
            float y = cos(t) + sin(t*0.03456)
            vm_gfx_pixel(screenx(x), screeny(y), color/16)
            t  += 0.01
            color++
        }
    }

    sub screenx(float x) -> word {
        return (x*width/4.1) + width / 2.0 as word
    }
    sub screeny(float y) -> word {
        return (y*height/4.1) + height / 2.0 as word
    }
}
