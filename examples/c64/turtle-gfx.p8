%import floats
%import graphics
%zeropage floatsafe


main {

    sub start() {
        graphics.enable_bitmap_mode()
        turtle.init()

        turtle.pu()
        turtle.pos(150, 110)
        turtle.pd()

        ubyte i
        for i in 0 to 100 {
            turtle.fd(i+20)
            turtle.rt(94)
        }

        repeat {
        }
    }
}

turtle {
    float xpos
    float ypos
    float angle
    bool pendown

    const uword SPRITE_MEMORY = $5800

    sub init() {
        xpos = 160.0
        ypos = 100.0
        angle = 0.0
        pendown = true

        sys.memcopy(&turtlesprite, SPRITE_MEMORY, len(turtlesprite))    ; copy the sprite pixel data
        c64.set_sprite_ptr(0, SPRITE_MEMORY)        ; use dynamic setter because of changed vic memory layout
        c64.SPENA = 1       ; sprites on
        c64.SP0COL = 5      ; green sprite

        update_turtle_sprite()
    }

    sub update_turtle_sprite() {
        uword xx = xpos as uword
        c64.SPXY[0] = lsb(xx) + 12
        c64.MSIGX = msb(xx)!=0 as ubyte
        c64.SPXY[1] = ypos as ubyte + 40
    }

    sub pos(float x, float y) {
        if pendown {
            graphics.line(xpos as uword, ypos as ubyte, x as uword, y as ubyte)
        }
        xpos = x
        ypos = y
        update_turtle_sprite()
    }

    sub fd(uword length) {
        float flen = length as float
        float sx = xpos
        float sy = ypos
        xpos += flen * floats.sin(angle)
        ypos -= flen * floats.cos(angle)
        update_turtle_sprite()
        if pendown {
            graphics.line(sx as uword, sy as ubyte, xpos as uword, ypos as ubyte)
        }
    }

    sub rt(uword degrees) {
        angle += floats.rad(degrees as float)
    }

    sub lt(uword degrees) {
        angle -= floats.rad(degrees as float)
    }

    sub pu() {
        pendown = false
    }

    sub pd() {
        pendown = true
    }

    ubyte[] turtlesprite = [ %00000000,%00000000,%00000000,
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
