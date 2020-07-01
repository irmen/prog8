%import c64lib
%import c64flt
%import c64graphics
%option enable_floats

; TODO fix compiler errors when compiling without optimizations

main {

    sub start() {
        graphics.enable_bitmap_mode()
        turtle.init()

        ubyte i
        for i in 0 to 255 {
            while c64.RASTER {
            }
        }

        for i in 0 to 100 {
            turtle.fd(i+20)
            turtle.rt(94)
        }

        forever {
        }
    }
}

turtle {
    float xpos
    float ypos
    float angle
    ubyte pendown

    sub init() {
        xpos = 160.0
        ypos = 100.0
        angle = 0.0
        pendown = true

        c64.SPRPTR[0] = $0d00 / 64
        c64.SPENA = 1
        c64.SP0COL = 5

        turtlepos()
    }

    sub turtlepos() {
        uword xx = xpos as uword
        c64.SPXY[0] = lsb(xx) + 12
        if msb(xx)
            c64.MSIGX = 1
        else
            c64.MSIGX = 0
        c64.SPXY[1] = lsb(ypos) + 40
    }

    sub fd(uword length) {
        float flen = length as float
        float sx = xpos
        float sy = ypos
        xpos += flen * sin(angle)
        ypos -= flen * cos(angle)
        turtlepos()
        if pendown {
            graphics.line(sx as uword, lsb(sy), xpos as uword, lsb(ypos))
        }
    }

    sub rt(uword degrees) {
        angle += rad(degrees as float)
    }

    sub lt(uword degrees) {
        angle -= rad(degrees as float)
    }

    sub pu() {
        pendown = false
    }

    sub pd() {
        pendown = true
    }
}

spritedata $0d00 {
    ; this memory block contains the sprite data
    ; it must start on an address aligned to 64 bytes.
    %option force_output    ; make sure the data in this block appears in the resulting program

    ubyte[] balloonsprite = [ %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%01111110,%00000000,
                              %00000000,%11000011,%00000000,
                              %00000000,%11000011,%00000000,
                              %00000000,%11000011,%00000000,
                              %00000000,%01111110,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000,
                              %00000000,%00000000,%00000000   ]
}
