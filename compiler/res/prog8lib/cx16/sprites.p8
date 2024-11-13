; Simple routines to control sprites.
; They're not written for high performance, but for simplicity.
; That's why they control 1 sprite at a time. The exception is pos_batch().
; which is quite efficient to update sprite positions of multiple sprites in one go.

; note: sprites z-order will be in front of all layers.
; note: collision mask is not supported here yet.
; note: "palette offset" is counted as 0-15  (vera multiplies the offset by 16 to get at the actual color index)

sprites {
    %option ignore_unused

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
             ubyte colors_flag, ubyte palette_offset) {
        pos(spritenum, -64, -64)                    ; move sprite off-screen initially
        cx16.VERA_DC_VIDEO |= %01000000             ; enable sprites globally
        dataaddr >>= 5
        dataaddr |= (databank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(dataaddr))                    ; address 12:5
        cx16.vpoke(1, sprite_reg+1, colors_flag | msb(dataaddr))    ; 4 bpp + address 16:13
        cx16.vpoke(1, sprite_reg+6, %00001100)                      ; z depth %11 = in front of both layers, no flips
        cx16.vpoke(1, sprite_reg+7, height_flag<<6 | width_flag<<4 | palette_offset&15) ; 64x64 pixels, palette offset
    }

    sub reset(ubyte spritenum_start, ubyte count)  {
        ; resets all sprite attributes for the given sprite range
        ; this removes these sprites from the screen completely
        ; (without disabling sprites globally so the mouse cursor remains visible)
        if spritenum_start > 127
            return
        if count + spritenum_start > 128
            return
        cx16.VERA_CTRL   = $00
        cx16.VERA_ADDR_H = $11
        cx16.VERA_ADDR   = VERA_SPRITEREGS + spritenum_start * $0008
        repeat count {
            unroll 8 cx16.VERA_DATA0 = $00
        }
    }

    sub data(ubyte spritenum, ubyte bank, uword addr) {
        addr >>= 5
        addr |= (bank as uword)<<11
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        cx16.vpoke(1, sprite_reg, lsb(addr))                    ; address 12:5
        cx16.vpoke_mask(1, sprite_reg+1, %11110000, msb(addr))    ; address 16:13
    }

    inline asmsub get_data_ptr(ubyte spritenum @A) -> ubyte @R1, uword @R0 {
        ; -- returns the VRAM address where the sprite's bitmap data is stored
        ;    R1 (byte) = the vera bank (0 or 1), R0 (word) = the address.
        %asm {{
            jsr  p8b_sprites.p8s_get_data_ptr_internal
        }}
    }

    sub get_data_ptr_internal(ubyte spritenum) {
        sprite_reg = VERA_SPRITEREGS + spritenum*$0008
        cx16.r0L = cx16.vpeek(1, sprite_reg)
        cx16.r0H = cx16.vpeek(1, sprite_reg+1)
        cx16.r1L = cx16.r0H & %00001000 !=0 as ubyte     ; bank
        cx16.r0 <<= 5                           ; address
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

    sub move(ubyte spritenum, word dx, word dy) {
        ; move a sprite based on its current position
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.r1s = mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word + dx
        cx16.r2s = mkword(cx16.vpeek(1, sprite_reg+3), cx16.vpeek(1, sprite_reg+2)) as word + dy
        cx16.vpoke(1, sprite_reg, cx16.r1L)
        cx16.vpoke(1, sprite_reg+1, cx16.r1H)
        cx16.vpoke(1, sprite_reg+2, cx16.r2L)
        cx16.vpoke(1, sprite_reg+3, cx16.r2H)
    }

    sub movex(ubyte spritenum, word dx) {
        ; move a sprite horizontally based on its current position
        sprite_reg = VERA_SPRITEREGS + 2 + spritenum*$0008
        cx16.r1s = mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word + dx
        cx16.vpoke(1, sprite_reg, cx16.r1L)
        cx16.vpoke(1, sprite_reg+1, cx16.r1H)
    }

    sub movey(ubyte spritenum, word dy) {
        ; move a sprite vertically based on its current position
        sprite_reg = VERA_SPRITEREGS + 4 + spritenum*$0008
        cx16.r1s = mkword(cx16.vpeek(1, sprite_reg+1), cx16.vpeek(1, sprite_reg)) as word + dy
        cx16.vpoke(1, sprite_reg, cx16.r1L)
        cx16.vpoke(1, sprite_reg+1, cx16.r1H)
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
        cx16.vpoke_and(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11110011)
    }

    sub show(ubyte spritenum) {
        cx16.vpoke_or(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %00001100)
    }

    sub zdepth(ubyte spritenum, ubyte depth) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11110011, depth<<2)
    }

    sub flipx(ubyte spritenum, bool flipped) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11111110, flipped as ubyte)
    }

    sub flipy(ubyte spritenum, bool flipped) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 6 + spritenum*$0008, %11111101, (flipped as ubyte)<<1)
    }

    sub set_palette_offset(ubyte spritenum, ubyte offset) {
        cx16.vpoke_mask(1, VERA_SPRITEREGS + 7 + spritenum*$0008, %11110000, offset&15)
    }

    sub set_mousepointer_hand() {
        ; the array below is the compressed form of this sprite image:
        ;    00, 16, 16, 00, 00, 00, 00, 00, 16, 16, 16, 00, 00, 00, 00, 00,
        ;    16, 01, 01, 16, 00, 00, 16, 16, 15, 01, 01, 16, 00, 00, 00, 00,
        ;    16, 01, 01, 01, 16, 16, 15, 01, 16, 15, 01, 01, 16, 00, 00, 00,
        ;    16, 15, 01, 01, 01, 01, 16, 15, 01, 01, 01, 01, 16, 00, 00, 00,
        ;    00, 16, 15, 01, 01, 01, 01, 01, 01, 01, 11, 01, 01, 16, 00, 00,
        ;    00, 00, 16, 15, 01, 01, 01, 01, 11, 01, 01, 11, 01, 16, 00, 00,
        ;    00, 00, 16, 16, 15, 01, 01, 01, 01, 11, 01, 01, 01, 16, 00, 00,
        ;    00, 00, 16, 12, 12, 15, 01, 01, 01, 01, 01, 01, 01, 16, 16, 00,
        ;    00, 00, 16, 12, 15, 15, 01, 01, 01, 01, 01, 01, 16, 01, 01, 16,
        ;    00, 00, 00, 16, 15, 15, 01, 01, 01, 01, 01, 01, 01, 01, 01, 16,
        ;    00, 00, 00, 00, 16, 15, 15, 15, 15, 15, 16, 01, 01, 15, 16, 00,
        ;    00, 00, 00, 00, 00, 16, 16, 16, 16, 16, 15, 01, 15, 16, 00, 00,
        ;    00, 00, 00, 00, 00, 00, 00, 00, 16, 12, 15, 15, 16, 00, 00, 00,
        ;    00, 00, 00, 00, 00, 00, 00, 00, 16, 12, 12, 16, 00, 00, 00, 00,
        ;    00, 00, 00, 00, 00, 00, 00, 00, 00, 16, 16, 00, 00, 00, 00, 00,
        ;    00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00
        ubyte[] hand_image_lzsa = [
            26, 31, 0, 16, 16, 0, 46, 16, 192, 58, 1, 1, 16, 156, 41, 15, 37,
            137, 42, 1, 6, 115, 2, 43, 1, 152, 34, 4, 127, 4, 15, 11, 116, 233,
            11, 37, 135, 6, 38, 7, 19, 12, 12, 67, 167, 35, 136, 237, 15, 77,
            16, 157, 37, 71, 157, 47, 2, 33, 24, 9, 15, 69, 83, 66, 94, 6, 136,
            77, 0, 186, 6, 69, 154, 6, 143, 70, 204, 15, 0, 191, 231, 232]

        set_mousepointer_image(hand_image_lzsa, true)
    }

    sub set_mousepointer_image(uword data, bool compressed) {
        get_data_ptr_internal(0)  ; the mouse cursor is sprite 0
        if cx16.r1L==0 and cx16.r0==0
            return    ; mouse cursor not enabled
        ubyte vbank = cx16.r1L
        cx16.vaddr(vbank, cx16.r0, 0, 1)
        if compressed {
            void cx16.memory_decompress(data, &cx16.VERA_DATA0)      ; decompress directly into vram
        } else {
            for cx16.r0L in 0 to 255 {
                cx16.VERA_DATA0 = data[cx16.r0L]
            }
        }
    }
}
