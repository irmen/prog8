%target c64
%import syslib
%import textio
%zeropage basicsafe

main {

    ubyte perform_scroll = false

    sub start() {
        c64.SPRPTR[0] = $0f00 / 64
        c64.SPENA = 1
        c64.SP0COL = 14
        c64.SPXY[0] = 80
        c64.SPXY[1] = 100

        c64.SCROLX &= %11110111     ; 38 column mode

        c64.set_rasterirq(1)     ; enable animation

        ubyte target_height = 10
        ubyte active_height = 24
        ubyte upwards = true

        repeat {
            ubyte mountain = 223        ; slope upwards
            if active_height < target_height {
                active_height++
                upwards = true
            } else if active_height > target_height {
                mountain = 233          ; slope downwards
                active_height--
                upwards = false
            } else {
                target_height = 8 + rnd() % 16
                if upwards
                    mountain = 233
                else
                    mountain = 223
            }

            while not perform_scroll {
                ; let the raster irq do its timing job
            }

            perform_scroll = false
            txt.scroll_left(true)
            if c64.RASTER & 1
                c64.SPXY[1] ++
            else
                c64.SPXY[1] --

            ubyte yy
            for yy in 0 to active_height-1 {
                txt.setcc(39, yy, 32, 2)         ; clear top of screen
            }
            txt.setcc(39, active_height, mountain, 8)    ; mountain edge
            for yy in active_height+1 to 24 {
                txt.setcc(39, yy, 160, 8)        ; draw mountain
            }

            yy = rnd()
            if yy > 100 {
                ; draw a star
                txt.setcc(39, yy % (active_height-1), '.', rnd())
            }

            if yy > 200 {
                ; draw a tree
                ubyte tree = 30
                ubyte treecolor = 5
                if yy & %01000000 != 0
                    tree = 88
                else if yy & %00100000 != 0
                    tree = 65
                if rnd() > 130
                    treecolor = 13
                txt.setcc(39, active_height, tree, treecolor)
            }

            if yy > 235 {
                ; draw a camel
                txt.setcc(39, active_height, 94, 9)
            }
        }
    }
}


spritedata $0f00 {
    ; this memory block contains the sprite data
    ; it must start on an address aligned to 64 bytes.
    %option force_output    ; make sure the data in this block appears in the resulting program

    ubyte[] balloonsprite = [ %00000000,%01111111,%00000000,
                              %00000001,%11111111,%11000000,
                              %00000011,%11111111,%11100000,
                              %00000011,%11100011,%11100000,
                              %00000111,%11011100,%11110000,
                              %00000111,%11011101,%11110000,
                              %00000111,%11011100,%11110000,
                              %00000011,%11100011,%11100000,
                              %00000011,%11111111,%11100000,
                              %00000011,%11111111,%11100000,
                              %00000010,%11111111,%10100000,
                              %00000001,%01111111,%01000000,
                              %00000001,%00111110,%01000000,
                              %00000000,%10011100,%10000000,
                              %00000000,%10011100,%10000000,
                              %00000000,%01001001,%00000000,
                              %00000000,%01001001,%00000000,
                              %00000000,%00111110,%00000000,
                              %00000000,%00111110,%00000000,
                              %00000000,%00111110,%00000000,
                              %00000000,%00011100,%00000000   ]
}


irq {
    ubyte smoothx=7
    sub irq() {
        smoothx = (smoothx-1) & 7
        main.perform_scroll = smoothx==0
        c64.SCROLX = (c64.SCROLX & %11111000) | smoothx
    }
}

