; Minimal sprite multiplexer experiment.
; Sixteen virtual sprites are displayed using eight hardware slots.
; Each slot is rewritten immediately after its 21-line sprite display.

%import syslib
%import math
%import sorting
%option no_sysinit

main {
    const ubyte NUM_INSTANCES = 16
    const ubyte NUM_HARDWARE = 8
    const uword spritedata_base = $3000

    ubyte[NUM_INSTANCES] sort_y
    uword[NUM_INSTANCES] @nosplit sort_order = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]
    ubyte[NUM_HARDWARE] current_y

    sub start() {
        sys.set_irqd()
        setup_sprite_graphics()
        setup_multiplexer()
        c64.SPENA = 255

        repeat {
            c64.EXTCOL = 1
            animate_x_positions()
            c64.EXTCOL = 2
            sort_instances()
            c64.EXTCOL = 0

            sys.waitvsync()
            c64.EXTCOL = 4
            ; Display sprites in sorted order, calculating timing dynamically
            for display_index in 0 to NUM_INSTANCES-1 {
                ubyte hw_sprite = display_index & 7
                ubyte virtual_sprite = lsb(sort_order[display_index])
                ^^Sprite sprite = sprites[virtual_sprite]

                ; Wait for the previous sprite in this slot to finish displaying
                if display_index >= NUM_HARDWARE {
                    ; Reusing a slot - wait for previous sprite to finish
                    uword target_line = (current_y[hw_sprite] as uword) + 22
                    uword raster_line = c64.RASTER
                    if (c64.SCROLY & %10000000) != 0
                        raster_line += 256

                    ; Smart timing: immediate if passed, minimum 3 lines if too close
                    if raster_line >= target_line {
                        ; Already past target, update immediately
                    } else {
                        uword gap = target_line - raster_line
                        if gap < 3 {
                            target_line = raster_line + 3
                        }
                        sys.waitrasterline(target_line)
                    }
                }

                ; Update sprite registers
                c64.SPXY[hw_sprite*2] = lsb(sprite.x)
                c64.SPCOL[hw_sprite] = sprite.color
                if sprite.x >= 256
                    c64.MSIGX |= msigx_setmask[hw_sprite]
                else
                    c64.MSIGX &= msigx_clearmask[hw_sprite]
                c64.SPXY[hw_sprite*2+1] = sprite.y
                current_y[hw_sprite] = sprite.y
            }
        }
    }

    sub setup_multiplexer() {
        sort_instances()
        for hw_sprite in 0 to NUM_HARDWARE-1 {
            ubyte instance = lsb(sort_order[hw_sprite])
            ^^Sprite sprite = sprites[instance]
            current_y[hw_sprite] = sprite.y
            c64.SPRPTR[hw_sprite] = lsb(spritedata_base/64)
            c64.SPXY[hw_sprite*2+1] = current_y[hw_sprite]
            c64.SPXY[hw_sprite*2] = lsb(sprite.x)
            c64.SPCOL[hw_sprite] = sprite.color
        }
        c64.MSIGX = 0
        for hw_sprite in 0 to NUM_HARDWARE-1 {
            if sprites[lsb(sort_order[hw_sprite])].x >= 256
                c64.MSIGX |= msigx_setmask[hw_sprite]
        }
    }

    ubyte tt
    sub animate_x_positions() {
        for virtual_sprite in 0 to NUM_INSTANCES-1 {
            ^^Sprite sprite = sprites[virtual_sprite]
            sprite.x++
            if sprite.x >= 340
                sprite.x = 0
        }

        ^^Sprite sprite0 = sprites[0]
        sprite0.y++
        sprite0 = sprites[1]
        sprite0.x--
        sprite0.x--
        if sprite0.x > 330
            sprite0.x = 330
        sprite0.y++
        sprite0.y++
        sprite0.y++
        sprite0 = sprites[2]
        sprite0.x = math.sin8u(tt)/2 + 100
        sprite0.y = math.cos8u(tt)/2 + 80
        tt++
    }

    sub sort_instances() {
        for pos in 0 to NUM_INSTANCES-1 {
            sort_y[pos] = sprites[lsb(sort_order[pos])].y
        }
        sorting.gnomesort_by_ub(sort_y, sort_order, NUM_INSTANCES)
    }

    sub setup_sprite_graphics() {
        ; Copy one sprite into a 64-byte-aligned area in VIC bank 0.
        sys.set_irqd()
        c64.banks(%011)
        sys.memcopy(balloonsprite, spritedata_base, 64)
        const uword sprdat = spritedata_base
        for cptr in 0 to 7
            sprdat[7+cptr*3] = @($d400+(sc:'a')*$0008+cptr)
        for i in 0 to NUM_INSTANCES-1 {
            ^^Sprite sprite = sprites[i]
            sprite.dataptr = lsb(spritedata_base/64)
        }
        c64.banks(%111)
    }

    struct Sprite {
        ubyte color
        ubyte dataptr
        uword x
        ubyte y
    }

    ^^Sprite[NUM_INSTANCES] sprites = [
        [1, $ff, 20, 40],
        [2, $ff, 40, 54],
        [3, $ff, 60, 68],
        [4, $ff, 80, 82],
        [5, $ff, 100, 96],
        [7, $ff, 120, 110],
        [8, $ff, 140, 124],
        [9, $ff, 160, 138],
        [10, $ff, 180, 152],
        [11, $ff, 200, 166],
        [12, $ff, 220, 180],
        [13, $ff, 240, 194],
        [14, $ff, 260, 208],
        [15, $ff, 280, 222],
        [1, $ff, 300, 236],
        [2, $ff, 320, 250]
    ]

    ubyte[8] msigx_setmask = [
        %00000001,
        %00000010,
        %00000100,
        %00001000,
        %00010000,
        %00100000,
        %01000000,
        %10000000
    ]

    ubyte[8] msigx_clearmask = [
        %11111110,
        %11111101,
        %11111011,
        %11110111,
        %11101111,
        %11011111,
        %10111111,
        %01111111
    ]

    ubyte[] balloonsprite = [
        %00000000,%01111111,%00000000,
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
        %00000000,%00011100,%00000000
    ]
}
