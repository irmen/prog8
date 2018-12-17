%import c64utils
%option enable_floats

~ main {

    memory ubyte[40] screen    = $0400

    poke()
    sub start()  {
        float t = 0.0
        while(true) {
            ubyte x = lsb(round(sin(t)*15.0))+20
            ubyte y = lsb(round(cos(t)*10.0))+12

;            vm_gfx_text(x, y, 1, "*")
            ;vm_gfx_pixel(x,y,1)
;            c64scr.print_ubyte_decimal(x)
;            c64.CHROUT(',')
;            c64scr.print_ubyte_decimal(y)
;            c64.CHROUT('\n')
            screen[x] = '*'
            t+=0.1
        }
    }
}

