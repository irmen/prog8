%import vera

%option ignore_unused

vera_sprites {
    ; Simple routines to control sprites.

    ; They're not written for high performance, but for simplicity.
    ; That's why they control 1 sprite at a time.
    ; The exceptions are pos_batch() and pos_batch_split(); these are quite efficient
    ; to update sprite positions of multiple sprites in one call.

    ; HIGH PERFORMANCE sprite handling would probably have a copy of the sprite registers for each sprite instead,
    ; and a unrolled loop that copies those into the VERA registers when needed.

    ; note: sprites z-order will be in front of all layers.
    ; note: collision mask is not supported here yet.
    ; note: "palette offset" is counted as 0-15  (vera multiplies the offset by 16 to get at the actual color index)

    const ubyte SIZE_8  = 0
    const ubyte SIZE_16 = 1
    const ubyte SIZE_32 = 2
    const ubyte SIZE_64 = 3
    const ubyte COLORS_16 = 0
    const ubyte COLORS_256 = 128
    const uword VERA_SPRITEREGS = $fc00     ; $1fc00
    private uword @zp sprite_reg

    sub init(ubyte spritenum,
             ubyte databank, uword dataaddr,
             ubyte width_flag, ubyte height_flag,
             ubyte colors_flag, ubyte palette_offset) {
        pos(spritenum, -64, -64)                    ; move sprite off-screen initially
        vera.VERA_DC_VIDEO |= %01000000             ; enable sprites globally
        dataaddr >>= 5
        dataaddr |= (databank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        vera.vpoke(1, sprite_reg, lsb(dataaddr))                    ; address 12:5
        vera.vpoke(1, sprite_reg+1, colors_flag | msb(dataaddr))    ; 4 bpp + address 16:13
        vera.vpoke(1, sprite_reg+6, %00001100)                      ; z depth %11 = in front of both layers, no flips
        vera.vpoke(1, sprite_reg+7, height_flag<<6 | width_flag<<4 | palette_offset&15) ; 64x64 pixels, palette offset
    }

    sub reset(ubyte spritenum_start, ubyte count)  {
        ; resets all sprite attributes for the given sprite range
        ; this removes these sprites from the screen completely
        ; (without disabling sprites globally so the mouse cursor remains visible)
        if spritenum_start > 127
            return
        if count + spritenum_start > 128
            return
        vera.VERA_CTRL   = $00
        vera.VERA_ADDR_H = $11
        vera.VERA_ADDR   = VERA_SPRITEREGS + spritenum_start * $0008
        repeat count {
            unroll 8 vera.VERA_DATA0 = $00
        }
    }

    sub data(ubyte spritenum, ubyte bank, uword addr) {
        addr >>= 5
        addr |= (bank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        vera.vpoke(1, sprite_reg, lsb(addr))                    ; address 12:5
        vera.vpoke_mask(1, sprite_reg+1, %11110000, msb(addr))    ; address 16:13
    }

    inline asmsub get_data_ptr(ubyte spritenum @A) -> ubyte @R1, uword @R0 {
        ; -- returns the VRAM address where the sprite's bitmap data is stored
        ;    R1 (byte) = the vera bank (0 or 1), R0 (word) = the address.
        %asm {{
            jsr  p8b_sprites.p8s_get_data_ptr_internal
        }}
    }

    private sub get_data_ptr_internal(ubyte spritenum) {
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        cx16.r0L = vera.vpeek(1, sprite_reg)
        cx16.r0H = vera.vpeek(1, sprite_reg+1)
        cx16.r1L = cx16.r0H & %00001000 !=0 as ubyte     ; bank
        cx16.r0 <<= 5                           ; address
    }

    sub pos(ubyte spritenum, word xpos, word ypos) {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        vera.vpoke(1, sprite_reg, lsb(xpos))
        vera.vpoke(1, sprite_reg+1, msb(xpos))
        vera.vpoke(1, sprite_reg+2, lsb(ypos))
        vera.vpoke(1, sprite_reg+3, msb(ypos))
    }

    sub pos_batch(ubyte first_spritenum, ubyte num_sprites, uword xpositions_ptr, uword ypositions_ptr) {
        ; -- note: the x and y positions word arrays must both be split word arrays in this version of the routine! (the default)
        sprite_reg = VERA_SPRITEREGS + 2 + first_spritenum*$0008
        vera.vaddr_autoincr(1, sprite_reg, 0, 8)
        vera.vaddr_autoincr(1, sprite_reg+1, 1, 8)
        tovera(xpositions_ptr)
        sprite_reg += 2
        vera.vaddr_autoincr(1, sprite_reg, 0, 8)
        vera.vaddr_autoincr(1, sprite_reg+1, 1, 8)
        tovera(ypositions_ptr)

        sub tovera(uword positions @R0) {
            repeat num_sprites {
                vera.VERA_DATA0 = @(cx16.r0)
                vera.VERA_DATA1 = @(cx16.r0+num_sprites)
                cx16.r0 ++
            }
        }
    }

    sub pos_batch_nosplit(ubyte first_spritenum, ubyte num_sprites, uword xpositions_ptr, uword ypositions_ptr) {
        ; -- note: the x and y positions word arrays must both be regular linear arrays in this version of the routine!
        sprite_reg = VERA_SPRITEREGS + 2 + first_spritenum*$0008
        vera.vaddr_autoincr(1, sprite_reg, 0, 8)
        vera.vaddr_autoincr(1, sprite_reg+1, 1, 8)
        tovera(xpositions_ptr)
        sprite_reg += 2
        vera.vaddr_autoincr(1, sprite_reg, 0, 8)
        vera.vaddr_autoincr(1, sprite_reg+1, 1, 8)
        tovera(ypositions_ptr)

        sub tovera(uword positions @R0) {
            repeat num_sprites {
                vera.VERA_DATA0 = @(cx16.r0)
                cx16.r0 ++
                vera.VERA_DATA1 = @(cx16.r0)
                cx16.r0 ++
            }
        }
    }

    sub setx(ubyte spritenum, word xpos) {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        vera.vpoke(1, sprite_reg, lsb(xpos))
        vera.vpoke(1, sprite_reg+1, msb(xpos))
    }

    sub sety(ubyte spritenum, word ypos) {
        sprite_reg = VERA_SPRITEREGS + 4 + spritenum*$0008
        vera.vpoke(1, sprite_reg, lsb(ypos))
        vera.vpoke(1, sprite_reg+1, msb(ypos))
    }

    sub move(ubyte spritenum, word dx, word dy) {
        ; move a sprite based on its current position
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.r1s = mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word + dx
        cx16.r2s = mkword(vera.vpeek(1, sprite_reg+3), vera.vpeek(1, sprite_reg+2)) as word + dy
        vera.vpoke(1, sprite_reg, cx16.r1L)
        vera.vpoke(1, sprite_reg+1, cx16.r1H)
        vera.vpoke(1, sprite_reg+2, cx16.r2L)
        vera.vpoke(1, sprite_reg+3, cx16.r2H)
    }

    sub movex(ubyte spritenum, word dx) {
        ; move a sprite horizontally based on its current position
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.r1s = mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word + dx
        vera.vpoke(1, sprite_reg, cx16.r1L)
        vera.vpoke(1, sprite_reg+1, cx16.r1H)
    }

    sub movey(ubyte spritenum, word dy) {
        ; move a sprite vertically based on its current position
        sprite_reg = VERA_SPRITEREGS + 4 + spritenum*$0008
        cx16.r1s = mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word + dy
        vera.vpoke(1, sprite_reg, cx16.r1L)
        vera.vpoke(1, sprite_reg+1, cx16.r1H)
    }

    sub getx(ubyte spritenum) -> word {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        return mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word
    }

    sub gety(ubyte spritenum) -> word {
        sprite_reg = VERA_SPRITEREGS + 4 + spritenum*$0008
        return mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word
    }

    sub getxy(ubyte spritenum) -> word, word {
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.r0s = mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word
        sprite_reg += 2
        cx16.r1s = mkword(vera.vpeek(1, sprite_reg+1), vera.vpeek(1, sprite_reg)) as word
        return cx16.r0s, cx16.r1s
    }

    sub hide(ubyte spritenum) {
        vera.vpoke_and(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11110011)
    }

    sub show(ubyte spritenum) {
        vera.vpoke_or(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %00001100)
    }

    sub zdepth(ubyte spritenum, ubyte depth) {
        vera.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11110011, depth<<2)
    }

    sub flipx(ubyte spritenum, bool flipped) {
        vera.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11111110, flipped as ubyte)
    }

    sub flipy(ubyte spritenum, bool flipped) {
        vera.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11111101, (flipped as ubyte)<<1)
    }

    sub set_palette_offset(ubyte spritenum, ubyte offset) {
        vera.vpoke_mask(1, VERA_SPRITEREGS + 7 + spritenum*$0008, %11110000, offset&15)
    }
}
