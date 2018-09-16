%output prg
%launcher basic
%option enable_floats

~ main {

sub start() -> () {

    const str cs1 = "string1"
    const str_p cs2 = "string2"
    const str_s cs3 = "string3"
    const str_ps cs4 = "string4"
    str vs1 = "string1"
    str_p vs2 = "string2"
    str_s vs3 = "string3"
    str_ps vs4 = "string4"
    const byte[5] ba = [1,2,3,4,5]
    const byte[5] ba2 = [1,2,3,4,50]
    const word[5] wa = [1,2,3,4,5]
    const word[5] wa2 = [1,2,3,4,500]
    const byte[100] yy = 1 to 100
    const byte[100] zz = 100 to 1 step -1
    const byte[7] xx = 1 to 20 step 3
    const byte[7] ww = 20 to 1 step -3
    const byte[2,4] wwm = 23 to 1 step -3
    const str derp2 = "a" to "z"

    _vm_write_str(cs1)
    _vm_write_str(cs2)
    _vm_write_str(cs3)
    _vm_write_str(cs4)
    _vm_write_str(vs1)
    _vm_write_str(vs2)
    _vm_write_str(vs3)
    _vm_write_str(vs4)

    word tx = 0
    word ty = 12 % 5
    float time = 0.0
    _vm_gfx_clearscr(0)

    for XY in 0 to 300 step 3 {
        _vm_gfx_pixel(XY, 2, XY)
        _vm_gfx_pixel(XY+1, 2, XY)
        _vm_gfx_pixel(XY, 3, XY)
        _vm_gfx_pixel(XY+1, 3, XY)
        ; continue
        ; break
    }
    for XY in 315 to 0 step -3 {
        _vm_gfx_pixel(XY, 6, XY)
        _vm_gfx_pixel(XY+1, 6, XY)
        _vm_gfx_pixel(XY, 7, XY)
        _vm_gfx_pixel(XY+1, 7, XY)
        ; continue
        ; break
    }

loop:
    tx = round(sin(time*1.01)*150 + 160)
    ty = round((cos(time)+sin(time/44.1))*60 + 128)
    _vm_gfx_pixel(tx, ty, rnd() % 4 + 7)
    time += 0.02
    goto loop
}
}
