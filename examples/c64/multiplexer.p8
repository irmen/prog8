; TODO in progress attempts to make a flexible sprite multiplexer
; For an easy sprite multiplexer (actually, just a sprite duplicator) see the "simplemultiplexer" example.
; inspiration: https://codebase64.net/doku.php?id=base:sprite_multiplexing

%import syslib
%import math
%import textio
%option no_sysinit

main {
    const ubyte NUM_VSPRITES = 16
    const uword spritedata_base = $3000     ; NOTE: in VIC bank 0 (and 2), the Char Rom is at $1000 so we can't store sprite data there

    sub start() {
        setup_sprite_graphics()
        c64.SPENA = 255    ; enable all sprites

        method1_wait_raster_before_each_sprite()

        ; Theoretically, I think all sprite Y values can be set in one go, and again after the last of the 8 hw sprites has started drawing, etc.
        ; There's no strict need to set it right before the sprite is starting to draw. But it only saves a few cycles?
        ; method2_after_old_sprite()

    }

    sub setup_sprite_graphics() {
        ; prepare the sprite graphics (put a different letter in each of the balloons)
        sys.set_irqd()
        c64.banks(%011)  ; enable CHAREN, so the character rom accessible at $d000 (inverse at $d400)
        ubyte i
        for i in 0 to NUM_VSPRITES-1 {
            uword sprdat = spritedata_base + $0040*i
            sys.memcopy(balloonsprite, sprdat, 64)
            ubyte cptr
            for cptr in 0 to 7 {
                sprdat[7+cptr*3] = @($d400+(i+sc:'a')*$0008 + cptr)
            }
            ^^Sprite sprite = sprites[i]
            sprite.dataptr = lsb(spritedata_base/64) + i
        }
        c64.banks(%111)     ; enable IO registers again
        ; sys.clear_irqd()  ; do not re-enable IRQ as this will glitch the display because it interferes with raster timing
    }

    sub method1_wait_raster_before_each_sprite() {
        repeat {
            animate_sprites()
            ; wait for raster lines and update hardware sprites
            ubyte vs_index
            for vs_index in 0 to NUM_VSPRITES-1 {
                ubyte virtual_sprite = sort_virtualsprite[vs_index]
                ubyte hw_sprite = virtual_sprite & 7
                ^^Sprite sprite = sprites[virtual_sprite]
                sys.waitrasterline(sprite.y-3)      ; wait for a few lines above the sprite so we have time to set all attributes properly
                c64.EXTCOL++
                c64.SPXY[hw_sprite*2+1] = sprite.y
                c64.SPXY[hw_sprite*2] = lsb(sprite.x)
                if sprite.x >= 256
                    c64.MSIGX |= msigx_setmask[hw_sprite]
                else
                    c64.MSIGX &= msigx_clearmask[hw_sprite]
                c64.SPCOL[hw_sprite] = sprite.color
                c64.SPRPTR[hw_sprite] = sprite.dataptr
                c64.EXTCOL--
            }
        }
    }

    ubyte tt
    sub animate_sprites() {
        tt += 2
        ubyte st = tt
        ubyte virtual_sprite
        for virtual_sprite in 0 to NUM_VSPRITES-1 {
            ^^Sprite sprite = sprites[virtual_sprite]
            sprite.x = $0028 + math.sin8u(st)
            ; TODO  nice anim   sprite.y = $50 + math.cos8u(st*3)/2
            st += 10
            sort_ypositions[virtual_sprite] = sprite.y
            sort_virtualsprite[virtual_sprite] = virtual_sprite
            ; TODO keep working with the previously sorted result instead of rewriting the list every time, makes sorting faster if not much changes in the Y positions
        }

        ; TODO remove this simplistic animation but it's here to test the algorithms
        sprite = sprites[0]
        sprite.y++
        sort_ypositions[0] = sprites[0].y
        sprite = sprites[1]
        sprite.y--
        sort_ypositions[1] = sprites[1].y

        ;c64.EXTCOL--
        sort_virtual_sprites()
        ;c64.EXTCOL++
    }

    ubyte[NUM_VSPRITES] sort_ypositions
    ubyte[NUM_VSPRITES] sort_virtualsprite

    ; TODO rewrite in assembly, sorting must be super fast
    sub sort_virtual_sprites() {
        ubyte @zp pos=1
        while pos != NUM_VSPRITES {
            if sort_ypositions[pos]>=sort_ypositions[pos-1]
                pos++
            else {
                ; swap elements
                cx16.r0L = pos-1
                swap(sort_ypositions[cx16.r0L], sort_ypositions[pos])
                ; swap virtual sprite indexes
                swap(sort_virtualsprite[cx16.r0L], sort_virtualsprite[pos])
                pos--
                if_z
                    pos++
            }
        }
    }


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

    struct Sprite {
        ubyte color
        ubyte dataptr
        uword x
        ubyte y
    }


    ^^Sprite[NUM_VSPRITES]  sprites = [
        [0, $ff, 20, 60],
        [1, $ff, 40, 70],
        [2, $ff, 60, 80],
        [3, $ff, 80, 90],
        [4, $ff, 100, 100],
        [5, $ff, 120, 110],
        [7, $ff, 140, 120],       ; skip color 6 (blue on blue)
        [8, $ff, 160, 130],
        [9, $ff, 180, 140],
        [10, $ff, 200, 150],
        [11, $ff, 220, 160],
        [12, $ff, 240, 170],
        [13, $ff, 260, 180],
        [14, $ff, 280, 190],
        [15, $ff, 300, 200],
        [0, $ff, 320, 210],
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

