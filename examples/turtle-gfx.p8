%target c64
%import floats
%import graphics
%import test_stack
%zeropage floatsafe

main {

    sub start() {
        graphics.enable_bitmap_mode()
        turtle.init()
;        turtle.pu()
;        turtle.pos(150, 110)
;        turtle.pd()

        ubyte i
        for i in 0 to 100 {
            turtle.fd(i+20)
            turtle.rt(94)
        }

        ; test_stack.test()

        repeat {
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

        update_turtle_sprite()
    }

    sub update_turtle_sprite() {
        uword xx = xpos as uword
        c64.SPXY[0] = lsb(xx) + 12
        c64.MSIGX = msb(xx) > 0
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
        xpos += flen * sin(angle)
        ypos -= flen * cos(angle)
        update_turtle_sprite()
        if pendown {
            graphics.line(sx as uword, sy as ubyte, xpos as uword, ypos as ubyte)
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
