; Simple routines to control sprites.
; They're not written for high performance, but for simplicity.
; That's why they control 1 sprite at a time. The exception is pos_batch().
; which is quite efficient to update sprite positions of multiple sprites in one go.

; note: sprites z-order will be in front of all layers.
; note: collision mask is not supported here yet.

sprites {
    const ubyte SIZE_8  = 0
    const ubyte SIZE_16 = 1
    const ubyte SIZE_32 = 2
    const ubyte SIZE_64 = 3
    const ubyte COLORS_16 = 0
    const ubyte COLORS_256 = 128
    const uword VERA_SPRITEREGS = $fc00     ; $1fc00
    uword @zp sprite_reg

    sub init(ubyte spritenum,
             ubyte databank, uword dataaddr,
             ubyte width_flag, ubyte height_flag,
             ubyte colors_flag, ubyte palette_offset_idx) {
        hide(spritenum)
        cx16.VERA_DC_VIDEO |= %01000000             ; enable sprites globally
        dataaddr >>= 5
        dataaddr |= (databank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(dataaddr))                    ; address 12:5
        cx16.vpoke(1, sprite_reg+1, colors_flag | msb(dataaddr))    ; 4 bpp + address 16:13
        cx16.vpoke(1, sprite_reg+6, %00001100)                      ; z depth %11 = in front of both layers, no flips
        cx16.vpoke(1, sprite_reg+7, height_flag<<6 | width_flag<<4 | palette_offset_idx>>4) ; 64x64 pixels, palette offset
    }

    sub data(ubyte spritenum, ubyte bank, uword addr) {
        addr >>= 5
        addr |= (bank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(addr))                    ; address 12:5
        cx16.vpoke_mask(1, sprite_reg+1, %11110000, msb(addr))    ; address 16:13
    }

    sub pos(ubyte spritenum, word xpos, word ypos) {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(xpos))
        cx16.vpoke(1, sprite_reg+1, msb(xpos))
        cx16.vpoke(1, sprite_reg+2, lsb(ypos))
        cx16.vpoke(1, sprite_reg+3, msb(ypos))
    }

    sub pos_batch(ubyte first_spritenum, ubyte num_sprites, uword xpositions_ptr, uword ypositions_ptr) {
        ; -- note: the x and y positions word arrays must be regular arrays, they cannot be @split arrays!
        sprite_reg = VERA_SPRITEREGS + 2 + first_spritenum*$0008
        cx16.vaddr_autoincr(1, sprite_reg, 0, 8)
        cx16.vaddr_autoincr(1, sprite_reg+1, 1, 8)
        repeat num_sprites {
            cx16.VERA_DATA0 = @(xpositions_ptr)
            xpositions_ptr ++
            cx16.VERA_DATA1 = @(xpositions_ptr)
            xpositions_ptr ++
        }
        sprite_reg += 2
        cx16.vaddr_autoincr(1, sprite_reg, 0, 8)
        cx16.vaddr_autoincr(1, sprite_reg+1, 1, 8)
        repeat num_sprites {
            cx16.VERA_DATA0 = @(ypositions_ptr)
            ypositions_ptr ++
            cx16.VERA_DATA1 = @(ypositions_ptr)
            ypositions_ptr ++
        }
    }

    sub setx(ubyte spritenum, word xpos) {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(xpos))
        cx16.vpoke(1, sprite_reg+1, msb(xpos))
    }

    sub sety(ubyte spritenum, word ypos) {
        sprite_reg = VERA_SPRITEREGS + 4 + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(ypos))
        cx16.vpoke(1, sprite_reg+1, msb(ypos))
    }

    sub getx(ubyte spritenum) -> word {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        return mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word
    }

    sub gety(ubyte spritenum) -> word {
        sprite_reg = VERA_SPRITEREGS + 4 + spritenum*$0008
        return mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word
    }

    sub hide(ubyte spritenum) {
        pos(spritenum, -64, -64)
    }

    sub flipx(ubyte spritenum, bool flipped) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11111110, flipped)
    }

    sub flipy(ubyte spritenum, bool flipped) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11111101, flipped<<1)
    }

    sub set_palette_offset(ubyte spritenum, ubyte offset) {
        sprite_reg = VERA_SPRITEREGS + 7 + spritenum*$0008
        cx16.vpoke_mask(1, sprite_reg, %11110000, offset>>4)
    }
}
