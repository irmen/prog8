%import diskio
%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.plot(32,30)
        txt.print("there be dragons!")

        ; load the sprite data and color palette directly into Vera ram
        void diskio.vload_raw("dragonsprite.bin", sprites.DATA_BANK, sprites.DATA_ADDR)
        void diskio.vload_raw("dragonsprite.pal", 1, $fa00 + sprites.PALETTE_OFFSET*2)

        ; initialize the dragon sprites
        sprites.init(1, sprites.DATA_BANK, sprites.DATA_ADDR, sprites.SIZE_64, sprites.SIZE_64)                ; top half of dragon
        sprites.init(2, sprites.DATA_BANK, sprites.DATA_ADDR + 64*64/2, sprites.SIZE_64, sprites.SIZE_64)      ; bottom half of dragon

        ubyte tt = 0
        word xpos = -64
        word ypos
        bool flippedx = false

        repeat {
            if flippedx
                xpos -= 2
            else
                xpos += 2

            if xpos >= 640 or xpos <= -64
                flippedx = not flippedx

            ypos = (240-64 as word) + math.sin8(tt)
            tt++

            txt.plot(32, 32)
            txt.print("at: ")
            txt.print_w(sprites.getx(1))
            txt.chrout(',')
            txt.print_w(sprites.gety(1))
            txt.print("   ")

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
    const ubyte SIZE_8  = 0
    const ubyte SIZE_16 = 1
    const ubyte SIZE_32 = 2
    const ubyte SIZE_64 = 3
    ; we choose arbitrary unused vram location for sprite data: $12000
    const ubyte DATA_BANK = 1
    const uword DATA_ADDR = $2000

    uword @zp sprite_reg

    sub init(ubyte sprite_num, ubyte data_bank, uword data_addr, ubyte width_flag, ubyte height_flag) {
        hide(sprite_num)
        cx16.VERA_DC_VIDEO |= %01000000             ; enable sprites globally
        data_addr >>= 5
        data_addr |= (data_bank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + sprite_num*$0008
        cx16.vpoke(1, sprite_reg, lsb(data_addr))                  ; address 12:5
        cx16.vpoke(1, sprite_reg+1, %00000000 | msb(data_addr))    ; 4 bpp + address 16:13
        cx16.vpoke(1, sprite_reg+6, %00001100)                     ; z depth %11 = in front of both layers, no flips
        cx16.vpoke(1, sprite_reg+7, height_flag<<6 | width_flag<<4 | PALETTE_OFFSET>>4) ; 64x64 pixels, palette offset
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

    sub pos(ubyte sprite_num, word xpos, word ypos) {
        sprite_reg = VERA_SPRITEREGS + 2 + sprite_num*$0008
        cx16.vpoke(1, sprite_reg, lsb(xpos))
        cx16.vpoke(1, sprite_reg+1, msb(xpos))
        cx16.vpoke(1, sprite_reg+2, lsb(ypos))
        cx16.vpoke(1, sprite_reg+3, msb(ypos))
    }

    sub setx(ubyte sprite_num, word xpos) {
        sprite_reg = VERA_SPRITEREGS + 2 + sprite_num*$0008
        cx16.vpoke(1, sprite_reg, lsb(xpos))
        cx16.vpoke(1, sprite_reg+1, msb(xpos))
    }

    sub sety(ubyte sprite_num, word ypos) {
        sprite_reg = VERA_SPRITEREGS + 4 + sprite_num*$0008
        cx16.vpoke(1, sprite_reg, lsb(ypos))
        cx16.vpoke(1, sprite_reg+1, msb(ypos))
    }

    sub getx(ubyte sprite_num) -> word {
        sprite_reg = VERA_SPRITEREGS + 2 + sprite_num*$0008
        return mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word
    }

    sub gety(ubyte sprite_num) -> word {
        sprite_reg = VERA_SPRITEREGS + 4 + sprite_num*$0008
        return mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word
    }
}
