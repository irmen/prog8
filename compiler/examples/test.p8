%output prg
%launcher basic
%option enable_floats

~ main {

sub start() -> () {

        const word width = 159
        const word height = 127
        word pixelx
        byte pixely
        float xx
        float yy
        float x = 4999.999
        float y
        float x2
        byte iter
        word plotx = 40000
        byte ploty

    ;yy = pixelx/width/3+0.2       ; @todo fix division to return float always, add // integer division
    ;xx = flt(pixelx)/width/3+0.2       ; @todo fix division to return float always, add // integer division

    _vm_write_num(plotx)
    _vm_write_char($8d)
    plotx //= 3      ; @todo fix division to return float always, add // integer division
    _vm_write_num(plotx)
    _vm_write_char($8d)

    x2 = x/33.33       ; @todo fix division to return float always, add // integer division
    _vm_write_num(x2)
    _vm_write_char($8d)
    x2 = x//33.33       ; @todo fix division to return float always, add // integer division
    _vm_write_num(x2)
    _vm_write_char($8d)
}
}
