%import c64utils
%import c64lib


~ main {

    sub start() {
        c64.SCROLY &= %11101111             ; blank the screen
        c64utils.set_rasterirq_excl(40)     ; register exclusive raster irq handler

        while(true) {
            ; enjoy the moving bars :)
        }

    }
}


~ irq {

    const ubyte barheight = 4
    ubyte[] colors = [6,2,4,5,15,7,1,13,3,12,8,11,9]
    ubyte color = 0
    ubyte yanim = 0

    sub irq() {
        if color!=len(colors) {
            c64.EXTCOL = colors[color]
            c64.RASTER += barheight         ; next raster Irq for next color
            color++
        }
        else {
            c64.EXTCOL = 0
            color = 0
            yanim += 2
            c64.RASTER = sin8u(yanim)/2+30  ; new start of raster Irq
        }
        c64.SCROLY &= $7f    ; set high bit of the raster pos to zero
    }
}
