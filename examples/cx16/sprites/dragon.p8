%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    const ubyte SPRITE_BANK = 0
    const uword SPRITE_ADDR = $2000


    sub start() {
        void diskio.vload_raw("dragonsprite.bin", SPRITE_BANK, SPRITE_ADDR)
        void diskio.vload_raw("dragonsprite.pal", 1, $fa00 + sprites.PALETTE_OFFSET)    ; load directly into vera color palette
        sprites.init(1, SPRITE_ADDR)                ; top half of dragon
        sprites.init(2, SPRITE_ADDR + 64*64/2)      ; bottom half of dragon

        word xpos = -64
        word ypos = 100
        ubyte tt = 0
        bool flipped

        repeat {
            if flipped
                xpos -= 2
            else
                xpos += 2

            if xpos >= 640 or xpos <= -64
                flipped = not flipped

            ypos = (240-64 as word) + math.sin8(tt)
            tt++
            sys.waitvsync()
            sprites.pos(1, xpos, ypos)
            sprites.pos(2, xpos, ypos+64)
            sprites.flipx(1, flipped)
            sprites.flipx(2, flipped)
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
    const ubyte PALETTE_OFFSET = 32         ; in bytes, so 16 color entries

    uword sprite_regs

    sub init(ubyte sprite_num, uword data_addr) {
        cx16.VERA_DC_VIDEO |= %01000000             ; enable sprites globally
        uword address = data_addr >> 5
        sprite_regs = VERA_SPRITEREGS + sprite_num*$0008
        cx16.vpoke(1, sprite_regs, lsb(address))                    ; address 12:5
        cx16.vpoke(1, sprite_regs+1, %00000000 | msb(address))      ; 4 bpp + address 16:13
        cx16.vpoke(1, sprite_regs+6, %00001100)                     ; sprite flags: enable sprite, z depth %11 = before both layers
        cx16.vpoke(1, sprite_regs+7, %11110000 | PALETTE_OFFSET>>5) ; 64x64 pixels, palette offset
        pos(sprite_num, 100, 100)
    }

    sub flipx(ubyte sprite_num, bool flipped) {
        uword sprite_reg = VERA_SPRITEREGS + sprite_num*$0008 + 6
        cx16.vpoke(1, sprite_reg, cx16.vpeek(1, sprite_reg) & %11111110 | flipped)
    }

    sub pos(ubyte sprite_num, word x, word y) {
        sprite_regs = VERA_SPRITEREGS + sprite_num*$0008
        cx16.vpoke(1, sprite_regs+2, lsb(x))
        cx16.vpoke(1, sprite_regs+3, msb(x))
        cx16.vpoke(1, sprite_regs+4, lsb(y))
        cx16.vpoke(1, sprite_regs+5, msb(y))
    }
}
