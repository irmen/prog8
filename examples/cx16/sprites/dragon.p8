%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    ; we choose arbitrary unused vram location for sprite data: $12000
    const ubyte SPRITE_DATA_BANK = 1
    const uword SPRITE_DATA_ADDR = $2000


    sub start() {
        void diskio.vload_raw("dragonsprite.bin", SPRITE_DATA_BANK, SPRITE_DATA_ADDR)
        void diskio.vload_raw("dragonsprite.pal", 1, $fa00 + sprites.PALETTE_OFFSET*2)    ; load directly into vera color palette
        sprites.init(1, SPRITE_DATA_BANK, SPRITE_DATA_ADDR)                ; top half of dragon
        sprites.init(2, SPRITE_DATA_BANK, SPRITE_DATA_ADDR + 64*64/2)      ; bottom half of dragon

        word xpos = -64
        word ypos = 100
        ubyte tt = 0
        bool flippedx

        repeat {
            if flippedx
                xpos -= 2
            else
                xpos += 2

            if xpos >= 640 or xpos <= -64
                flippedx = not flippedx

            ypos = (240-64 as word) + math.sin8(tt)
            tt++

            sys.waitvsync()
            sprites.pos(1, xpos, ypos)
            sprites.pos(2, xpos, ypos+64)
            sprites.flipx(1, flippedx)
            sprites.flipx(2, flippedx)
        }
    }
}

sprites {
    ; sprite registers base in VRAM:  $1fc00
    ;        Sprite 0:          $1FC00 - $1FC07     ; used by the kernal for mouse pointer
    ;        Sprite 1:          $1FC08 - $1FC0F
    ;        Sprite 2:          $1FC10 - $1FC17
    ;        …
    ;        Sprite 127:        $1FFF8 - $1FFFF
    const uword VERA_SPRITEREGS = $fc00     ; $1fc00
    const ubyte PALETTE_OFFSET = 16         ; color palette indices 16-31

    uword @zp sprite_reg

    sub init(ubyte sprite_num, ubyte data_bank, uword data_addr) {
        hide(sprite_num)
        cx16.VERA_DC_VIDEO |= %01000000             ; enable sprites globally
        data_addr >>= 5
        data_addr |= (data_bank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + sprite_num*$0008
        cx16.vpoke(1, sprite_reg, lsb(data_addr))                  ; address 12:5
        cx16.vpoke(1, sprite_reg+1, %00000000 | msb(data_addr))    ; 4 bpp + address 16:13
        cx16.vpoke(1, sprite_reg+6, %00001100)                     ; z depth %11 = in front of both layers, no flips
        cx16.vpoke(1, sprite_reg+7, %11110000 | PALETTE_OFFSET>>4) ; 64x64 pixels, palette offset
    }

    sub hide(ubyte sprite_num) {
        pos(sprite_num, -64, -64)
    }

    sub flipx(ubyte sprite_num, bool flipped) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + sprite_num*$0008, %11111110, flipped)
    }

    sub flipy(ubyte sprite_num, bool flipped) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + sprite_num*$0008, %11111101, flipped<<1)
    }

    sub pos(ubyte sprite_num, word x, word y) {
        sprite_reg = VERA_SPRITEREGS + 2 + sprite_num*$0008
        cx16.vpoke(1, sprite_reg, lsb(x))
        cx16.vpoke(1, sprite_reg+1, msb(x))
        cx16.vpoke(1, sprite_reg+2, lsb(y))
        cx16.vpoke(1, sprite_reg+3, msb(y))
    }
}
