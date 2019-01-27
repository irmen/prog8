%import c64utils
%import c64lib


~ main {

    sub start() {
        c64.SCROLY &= %11101111           ; blank the screen
        c64utils.set_rasterirq_excl(40)

        while(true) {
        }

    }
}


~ irq {

    const ubyte barheight = 4
    ubyte[13] colors = [6,2,4,5,15,7,1,13,3,12,8,11,9]
    ubyte color = 0
    ubyte ypos = 0

    sub irq() {
        Y++   ; delay for alignment
        Y++   ; delay for alignment
        Y++   ; delay for alignment
        Y++   ; delay for alignment
        ubyte rasterpos = c64.RASTER
        if color!=len(colors) {
            c64.EXTCOL = colors[color]
            c64.RASTER = rasterpos+barheight
            color++
        }
        else {
            Y++   ; delay for alignment
            Y++   ; delay for alignment
            ypos += 2
            c64.EXTCOL = 0
            c64.RASTER = sin8u(ypos)/2+40
            color = 0
        }
    }
}
