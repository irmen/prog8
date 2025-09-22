; TODO attempts to make a flexible sprite multiplexer
; For an easy sprite multiplexer (actually, just a sprite duplicator) see the "simplemultiplexer" example.

%import syslib
%option no_sysinit

main {
    const ubyte NUM_SPRITES = 16

    uword spritedata_base = memory("spritedata", 64*NUM_SPRITES, 64)

    sub start() {
        ^^Sprite sprite

        ; first prepare the sprite graphics (put a different letter in each of the balloons)
        sys.set_irqd()
        c64.banks(%011)  ; enable CHAREN, so the character rom accessible at $d000 (inverse at $d400)
        ubyte i
        for i in 0 to NUM_SPRITES-1 {
            uword sprdat = spritedata_base + $0040*i
            sys.memcopy(balloonsprite, sprdat, 64)
            ubyte cptr
            for cptr in 0 to 7 {
                sprdat[7+cptr*3] = @($d400+(i+sc:'a')*$0008 + cptr)
            }
            sprite = sprites[i]
            sprite.dataptr = lsb(spritedata_base/64) + i
        }
        c64.banks(%111)     ; enable IO registers again
        ; sys.clear_irqd()  ; do not re-enable IRQ as this will glitch the display because it interferes with raster timing
        c64.SPENA = 255    ; enable all sprites

        ; Theoretically, all sprite Y values can be set in one go, and again after the last of the 8 hw sprites has started drawing, etc.
        ; There's no strict need to set it right before the sprite is starting to draw. But it only saves a few cycles?
        repeat {
            c64.MSIGX = 0
            for i in 0 to 7 {
                sprite = sprites[i]
                while c64.RASTER != sprite.y-4 or c64.SCROLY&$80!=0 {
                    ; wait till line before sprite starts
                }
                c64.EXTCOL++
                if sprite.x >= 256 {
                    c64.MSIGX |= msigx[i]
                }
                c64.SPXY[i*2] = lsb(sprite.x)
                c64.SPXY[i*2+1] = sprite.y
                c64.SPCOL[i] = sprite.color
                c64.SPRPTR[i] = sprite.dataptr
                c64.EXTCOL--
            }

            c64.MSIGX = 0
            for i in 0 to 7 {
                sprite = sprites[i+8]
                while c64.RASTER != sprite.y-4 or c64.SCROLY&$80!=0 {
                    ; wait till line a bit before sprite starts
                }
                c64.EXTCOL++
                if sprite.x >= 256 {
                    c64.MSIGX |= msigx[i]
                }
                c64.SPXY[i*2] = lsb(sprite.x)
                c64.SPXY[i*2+1] = sprite.y
                c64.SPCOL[i] = sprite.color
                c64.SPRPTR[i] = sprite.dataptr
                c64.EXTCOL--
            }

            sys.waitvsync()
        }
    }

    ubyte[8] msigx = [
        %00000001,
        %00000010,
        %00000100,
        %00001000,
        %00010000,
        %00100000,
        %01000000,
        %10000000,
    ]

    struct Sprite {
        ubyte color
        ubyte dataptr
        uword x
        ubyte y
    }


    ^^Sprite[NUM_SPRITES] @shared sprites = [
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

