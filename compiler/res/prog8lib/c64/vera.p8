%option ignore_unused

vera {
    ; VERA register definitions and helper routines for the 'VERA 64' board for the C64

    %option ignore_unused, no_symbol_prefixing

    const uword VERA_BASE       = $DE00     ; TODO to be decided?

    &ubyte  VERA_ADDR_L         = VERA_BASE + $0000
    &ubyte  VERA_ADDR_M         = VERA_BASE + $0001
    &uword  VERA_ADDR           = VERA_BASE + $0000 ; still need to do the _H separately
    &ubyte  VERA_ADDR_H         = VERA_BASE + $0002
    &ubyte  VERA_DATA0          = VERA_BASE + $0003
    &ubyte  VERA_DATA1          = VERA_BASE + $0004
    &ubyte  VERA_CTRL           = VERA_BASE + $0005
    &ubyte  VERA_IEN            = VERA_BASE + $0006
    &ubyte  VERA_ISR            = VERA_BASE + $0007
    &ubyte  VERA_IRQLINE_L      = VERA_BASE + $0008 ; write only
    &ubyte  VERA_SCANLINE_L     = VERA_BASE + $0008 ; read only
    &ubyte  VERA_DC_VIDEO       = VERA_BASE + $0009 ; DCSEL= 0
    &ubyte  VERA_DC_HSCALE      = VERA_BASE + $000A ; DCSEL= 0
    &ubyte  VERA_DC_VSCALE      = VERA_BASE + $000B ; DCSEL= 0
    &ubyte  VERA_DC_BORDER      = VERA_BASE + $000C ; DCSEL= 0
    &ubyte  VERA_DC_HSTART      = VERA_BASE + $0009 ; DCSEL= 1
    &ubyte  VERA_DC_HSTOP       = VERA_BASE + $000A ; DCSEL= 1
    &ubyte  VERA_DC_VSTART      = VERA_BASE + $000B ; DCSEL= 1
    &ubyte  VERA_DC_VSTOP       = VERA_BASE + $000C ; DCSEL= 1
    &ubyte  VERA_DC_VER0        = VERA_BASE + $0009 ; DCSEL=63
    &ubyte  VERA_DC_VER1        = VERA_BASE + $000A ; DCSEL=63
    &ubyte  VERA_DC_VER2        = VERA_BASE + $000B ; DCSEL=63
    &ubyte  VERA_DC_VER3        = VERA_BASE + $000C ; DCSEL=63
    &ubyte  VERA_L0_CONFIG      = VERA_BASE + $000D
    &ubyte  VERA_L0_MAPBASE     = VERA_BASE + $000E
    &ubyte  VERA_L0_TILEBASE    = VERA_BASE + $000F
    &ubyte  VERA_L0_HSCROLL_L   = VERA_BASE + $0010
    &ubyte  VERA_L0_HSCROLL_H   = VERA_BASE + $0011
    &uword  VERA_L0_HSCROLL     = VERA_BASE + $0010
    &ubyte  VERA_L0_VSCROLL_L   = VERA_BASE + $0012
    &ubyte  VERA_L0_VSCROLL_H   = VERA_BASE + $0013
    &uword  VERA_L0_VSCROLL     = VERA_BASE + $0012
    &ubyte  VERA_L1_CONFIG      = VERA_BASE + $0014
    &ubyte  VERA_L1_MAPBASE     = VERA_BASE + $0015
    &ubyte  VERA_L1_TILEBASE    = VERA_BASE + $0016
    &ubyte  VERA_L1_HSCROLL_L   = VERA_BASE + $0017
    &ubyte  VERA_L1_HSCROLL_H   = VERA_BASE + $0018
    &uword  VERA_L1_HSCROLL     = VERA_BASE + $0017
    &ubyte  VERA_L1_VSCROLL_L   = VERA_BASE + $0019
    &ubyte  VERA_L1_VSCROLL_H   = VERA_BASE + $001A
    &uword  VERA_L1_VSCROLL     = VERA_BASE + $0019
    &ubyte  VERA_AUDIO_CTRL     = VERA_BASE + $001B
    &ubyte  VERA_AUDIO_RATE     = VERA_BASE + $001C
    &ubyte  VERA_AUDIO_DATA     = VERA_BASE + $001D
    &ubyte  VERA_SPI_DATA       = VERA_BASE + $001E
    &ubyte  VERA_SPI_CTRL       = VERA_BASE + $001F

    ; Vera FX registers: (accessing depends on particular DCSEL value set in VERA_CTRL!)
    &ubyte  VERA_FX_CTRL        = VERA_BASE + $0009
    &ubyte  VERA_FX_TILEBASE    = VERA_BASE + $000a
    &ubyte  VERA_FX_MAPBASE     = VERA_BASE + $000b
    &ubyte  VERA_FX_MULT        = VERA_BASE + $000c
    &ubyte  VERA_FX_X_INCR_L    = VERA_BASE + $0009
    &ubyte  VERA_FX_X_INCR_H    = VERA_BASE + $000a
    &uword  VERA_FX_X_INCR      = VERA_BASE + $0009
    &ubyte  VERA_FX_Y_INCR_L    = VERA_BASE + $000b
    &ubyte  VERA_FX_Y_INCR_H    = VERA_BASE + $000c
    &uword  VERA_FX_Y_INCR      = VERA_BASE + $000b
    &ubyte  VERA_FX_X_POS_L     = VERA_BASE + $0009
    &ubyte  VERA_FX_X_POS_H     = VERA_BASE + $000a
    &uword  VERA_FX_X_POS       = VERA_BASE + $0009
    &ubyte  VERA_FX_Y_POS_L     = VERA_BASE + $000b
    &ubyte  VERA_FX_Y_POS_H     = VERA_BASE + $000c
    &uword  VERA_FX_Y_POS       = VERA_BASE + $000b
    &ubyte  VERA_FX_X_POS_S     = VERA_BASE + $0009
    &ubyte  VERA_FX_Y_POS_S     = VERA_BASE + $000a
    &ubyte  VERA_FX_POLY_FILL_L = VERA_BASE + $000b
    &ubyte  VERA_FX_POLY_FILL_H = VERA_BASE + $000c
    &uword  VERA_FX_POLY_FILL   = VERA_BASE + $000b
    &ubyte  VERA_FX_CACHE_L     = VERA_BASE + $0009
    &ubyte  VERA_FX_CACHE_M     = VERA_BASE + $000a
    &ubyte  VERA_FX_CACHE_H     = VERA_BASE + $000b
    &ubyte  VERA_FX_CACHE_U     = VERA_BASE + $000c
    &ubyte  VERA_FX_ACCUM       = VERA_BASE + $000a
    &ubyte  VERA_FX_ACCUM_RESET = VERA_BASE + $0009


    sub reset() {
        VERA_CTRL = %10000000    ; bit 7 = Reset Adapter
        ; the fpga takes milliseconds to reconfigure, so wait until vera
        ; responds again by polling a register write/readback until it sticks
        repeat {
            VERA_ADDR_L = $42
            if VERA_ADDR_L == $42 {
                break
            }
        }
        VERA_CTRL = 0
    }

asmsub vpeek(ubyte bank @A, uword address @XY) -> ubyte @A {
        ; -- get a byte from VERA's video memory
        ;    note: inefficient when reading multiple sequential bytes!
        %asm {{
                pha
                lda  #0
                sta  VERA_CTRL
                pla
                sta  VERA_ADDR_H
                sty  VERA_ADDR_M
                stx  VERA_ADDR_L
                lda  VERA_DATA0
                rts
            }}
}

asmsub vaddr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, byte autoIncrOrDecrByOne @Y) clobbers(A) {
        ; -- setup the VERA's data address register 0 or 1 with optional auto increment or decrement of 1.
        ;    This is a convenience routine, and not very efficient if you call it often;
        ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
        ;    Note that the vaddr_autoincr() and vaddr_autodecr() routines allow to set all possible strides, not just 1.
        ;    Note also that Vera's addrset is reset to 0 on exit, even if you set port #1's address.
        %asm {{
            pha
            lda  cx16.r1
            and  #1
            sta  VERA_CTRL
            lda  cx16.r0
            sta  VERA_ADDR_L
            lda  cx16.r0+1
            sta  VERA_ADDR_M
            pla
            cpy  #0
            bmi  ++
            beq  +
            ora  #%00010000
+           sta  VERA_ADDR_H
            lda  #0
            sta  VERA_CTRL
            rts
+           ora  #%00011000
            sta  VERA_ADDR_H
            lda  #0
            sta  VERA_CTRL
            rts
        }}
}

asmsub vaddr_clone(ubyte port @A) clobbers (A,X,Y) {
    ; -- clones Vera addresses from the given source port to the other one.
    ;    This is a convenience routine, and not very efficient if you call it often;
    ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
    %asm {{
        sta  VERA_CTRL
        ldx  VERA_ADDR_L
        ldy  VERA_ADDR_H
        sty  P8ZP_SCRATCH_B1
        ldy  VERA_ADDR_M
        eor  #1
        sta  VERA_CTRL
        stx  VERA_ADDR_L
        sty  VERA_ADDR_M
        ldy  P8ZP_SCRATCH_B1
        sty  VERA_ADDR_H
        lda  #0
        sta  VERA_CTRL
        rts
    }}
}

asmsub vaddr_autoincr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, uword autoIncrAmount @R2) clobbers(A,Y) {
        ; -- setup the VERA's data address register 0 or 1, including setting up optional auto increment amount.
        ;    Specifiying an unsupported amount results in amount of zero. See the Vera docs about what amounts are possible.
        ;    This is a convenience routine, and not very efficient if you call it often;
        ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
        %asm {{
            jsr  _setup
            lda  cx16.r2H
            ora  cx16.r2L
            beq  +
            jsr  _determine_incr_bits
+           ora  P8ZP_SCRATCH_REG
            sta  VERA_ADDR_H
            lda  #0
            sta  VERA_CTRL
            rts

_setup      sta  P8ZP_SCRATCH_REG
            lda  cx16.r1
            and  #1
            sta  VERA_CTRL
            lda  cx16.r0
            sta  VERA_ADDR_L
            lda  cx16.r0+1
            sta  VERA_ADDR_M
            rts

_determine_incr_bits
            lda  cx16.r2H
            bne  _large
            lda  cx16.r2L
            ldy  #13
-           cmp  _strides_lsb,y
            beq  +
            dey
            bpl  -
+           tya
            asl  a
            asl  a
            asl  a
            asl  a
            rts
_large      ora  cx16.r2L
            cmp  #1         ; 256
            bne  +
            lda  #9<<4
            rts
+           cmp  #2         ; 512
            bne  +
            lda  #10<<4
            rts
+           cmp  #65        ; 320
            bne  +
            lda  #14<<4
            rts
+           cmp  #130       ; 640
            bne  +
            lda  #15<<4
            rts
+           lda  #0
            rts
_strides_lsb    .byte   0,1,2,4,8,16,32,64,128,255,255,40,80,160,255,255
            ; !notreached!
        }}
}

asmsub vaddr_autodecr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, uword autoDecrAmount @R2) clobbers(A,Y) {
        ; -- setup the VERA's data address register 0 or 1 including setting up optional auto decrement amount.
        ;    Specifiying an unsupported amount results in amount of zero. See the Vera docs about what amounts are possible.
        ;    This is a convenience routine, and not very efficient if you call it often;
        ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
        %asm {{
            jsr  vaddr_autoincr._setup
            lda  cx16.r2H
            ora  cx16.r2L
            beq  +
            jsr  vaddr_autoincr._determine_incr_bits
            ora  #%00001000         ; autodecrement
+           ora  P8ZP_SCRATCH_REG
            sta  VERA_ADDR_H
            lda  #0
            sta  VERA_CTRL
            rts
        }}
}

asmsub vpoke(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers(A) {
    ; -- write a single byte to VERA's video memory
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        pha
        lda  #0
        sta  VERA_CTRL
        pla
        sta  VERA_ADDR_H
        lda  cx16.r0
        sta  VERA_ADDR_L
        lda  cx16.r0+1
        sta  VERA_ADDR_M
        sty  VERA_DATA0
        rts
    }}
}

asmsub vpoke_or(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers (A) {
    ; -- or a single byte to the value already in the VERA's video memory at that location
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        pha
        lda  #0
        sta  VERA_CTRL
        pla
        sta  VERA_ADDR_H
        lda  cx16.r0
        sta  VERA_ADDR_L
        lda  cx16.r0+1
        sta  VERA_ADDR_M
        tya
        ora  VERA_DATA0
        sta  VERA_DATA0
        rts
    }}
}

asmsub vpoke_and(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers(A) {
    ; -- and a single byte to the value already in the VERA's video memory at that location
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        pha
        lda  #0
        sta  VERA_CTRL
        pla
        sta  VERA_ADDR_H
        lda  cx16.r0
        sta  VERA_ADDR_L
        lda  cx16.r0+1
        sta  VERA_ADDR_M
        tya
        and  VERA_DATA0
        sta  VERA_DATA0
        rts
    }}
}

asmsub vpoke_xor(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers (A) {
    ; -- xor a single byte to the value already in the VERA's video memory at that location
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        pha
        lda  #0
        sta  VERA_CTRL
        pla
        sta  VERA_ADDR_H
        lda  cx16.r0
        sta  VERA_ADDR_L
        lda  cx16.r0+1
        sta  VERA_ADDR_M
        tya
        eor  VERA_DATA0
        sta  VERA_DATA0
        rts
    }}
}

asmsub vpoke_mask(ubyte bank @A, uword address @R0, ubyte mask @X, ubyte value @Y) clobbers (A) {
    ; -- bitwise or a single byte to the value already in the VERA's video memory at that location
    ;    after applying the and-mask. Note: inefficient when writing multiple sequential bytes!
    %asm {{
        sty  P8ZP_SCRATCH_B1
        pha
        lda  #0
        sta  VERA_CTRL
        pla
        sta  VERA_ADDR_H
        lda  cx16.r0
        sta  VERA_ADDR_L
        lda  cx16.r0+1
        sta  VERA_ADDR_M
        txa
        and  VERA_DATA0
        ora  P8ZP_SCRATCH_B1
        sta  VERA_DATA0
        rts
    }}
}

asmsub save_virtual_registers() clobbers(A,Y) {
    %asm {{
        ldy  #31
-       lda  cx16.r0,y
        sta  _cx16_vreg_storage,y
        dey
        bpl  -
        rts

        .section BSS
_cx16_vreg_storage
        .word ?,?,?,?,?,?,?,?
        .word ?,?,?,?,?,?,?,?
        .send BSS
        ; !notreached!
    }}
}

asmsub restore_virtual_registers() clobbers(A,Y) {
    %asm {{
        ldy  #31
-       lda  save_virtual_registers._cx16_vreg_storage,y
        sta  cx16.r0,y
        dey
        bpl  -
        rts
    }}
}


asmsub save_vera_context() clobbers(A) {
    ; -- use this at the start of your IRQ handler if it uses Vera registers, to save the state
    %asm {{
        ; note cannot store this on cpu hardware stack because this gets called as a subroutine
        lda  VERA_ADDR_L
        sta  _vera_storage
        lda  VERA_ADDR_M
        sta  _vera_storage+1
        lda  VERA_ADDR_H
        sta  _vera_storage+2
        lda  VERA_CTRL
        sta  _vera_storage+3
        eor  #1
        sta  _vera_storage+7
        sta  VERA_CTRL
        lda  VERA_ADDR_L
        sta  _vera_storage+4
        lda  VERA_ADDR_M
        sta  _vera_storage+5
        lda  VERA_ADDR_H
        sta  _vera_storage+6
        rts
        .section BSS
_vera_storage:  .byte ?,?,?,?,?,?,?,?
        .send BSS
        ; !notreached!
    }}
}

asmsub restore_vera_context() clobbers(A) {
    ; -- use this at the end of your IRQ handler if it uses Vera registers, to restore the state
    %asm {{
        lda  vera.save_vera_context._vera_storage+7
        sta  VERA_CTRL
        lda  vera.save_vera_context._vera_storage+6
        sta  VERA_ADDR_H
        lda  vera.save_vera_context._vera_storage+5
        sta  VERA_ADDR_M
        lda  vera.save_vera_context._vera_storage+4
        sta  VERA_ADDR_L
        lda  vera.save_vera_context._vera_storage+3
        sta  VERA_CTRL
        lda  vera.save_vera_context._vera_storage+2
        sta  VERA_ADDR_H
        lda  vera.save_vera_context._vera_storage+1
        sta  VERA_ADDR_M
        lda  vera.save_vera_context._vera_storage+0
        sta  VERA_ADDR_L
        rts
    }}
}

}

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

vera_palette {
    ; Manipulate the Vera's display color vera_palette.
    ; Should you want to restore the full default palette, you can call cbm.CINT()
    ; The first 16 colors can be restored to their default with set_default16()
    ; NOTE: assume R0, R1 and R2 are clobbered when using routines in this library!

    %option ignore_unused

    sub set_color(ubyte index, uword color) {
        internal_set_vera_palette_addr(index)
        vera.VERA_DATA0 = lsb(color)
        vera.VERA_DATA0 = msb(color)
        vera.VERA_ADDR_H &= 1
    }

    sub get_color(ubyte index) -> uword {
        internal_set_vera_palette_addr(index)
        cx16.r0L = vera.VERA_DATA0
        cx16.r0H = vera.VERA_DATA0
        vera.VERA_ADDR_H &= 1
        return cx16.r0
    }

    sub set_rgb_be(uword palette_ptr, uword num_colors, ubyte startindex) {
        ; 1 word per color entry, $0rgb in big endian format (split arrays)
        internal_set_vera_palette_addr(startindex)
        repeat num_colors {
            vera.VERA_DATA0 = @(palette_ptr+num_colors)
            vera.VERA_DATA0 = @(palette_ptr)
            palette_ptr++
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_rgb_be_nosplit(uword palette_ptr, uword num_colors, ubyte startindex) {
        ; 1 word per color entry, $0rgb in big endian format (linear arrays)
        internal_set_vera_palette_addr(startindex)
        repeat num_colors {
            vera.VERA_DATA0 = @(palette_ptr+1)
            vera.VERA_DATA0 = @(palette_ptr)
            palette_ptr += 2
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_rgb(uword palette_words_ptr, uword num_colors, ubyte startindex) {
        ; 1 word per color entry (in little endian format as layed out in video memory, so $gb;$0r)  (split arrays)
        internal_set_vera_palette_addr(startindex)
        repeat num_colors {
            vera.VERA_DATA0 = @(palette_words_ptr)
            vera.VERA_DATA0 = @(palette_words_ptr+num_colors)
            palette_words_ptr++
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_rgb_nosplit(uword palette_words_ptr, uword num_colors, ubyte startindex) {
        ; 1 word per color entry (in little endian format as layed out in video memory, so $gb;$0r)  (linear arrays)
        internal_set_vera_palette_addr(startindex)
        repeat num_colors {
            vera.VERA_DATA0 = @(palette_words_ptr)
            palette_words_ptr++
            vera.VERA_DATA0 = @(palette_words_ptr)
            palette_words_ptr++
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_rgb8(uword palette_bytes_ptr, uword num_colors, ubyte startindex) {
        ; 3 bytes per color entry, adjust color depth from 8 to 4 bits per channel.
        internal_set_vera_palette_addr(startindex)
        ubyte red
        ubyte greenblue
        repeat num_colors {
            cx16.r1 = color8to4(palette_bytes_ptr)
            palette_bytes_ptr+=3
            vera.VERA_DATA0 = cx16.r1H  ; $GB
            vera.VERA_DATA0 = cx16.r1L  ; $0R
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_all_black() {
        internal_set_vera_palette_addr(0)
        repeat 256 {
            vera.VERA_DATA0 = 0
            vera.VERA_DATA0 = 0
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_all_white() {
        internal_set_vera_palette_addr(0)
        repeat 256 {
            vera.VERA_DATA0 = $ff
            vera.VERA_DATA0 = $0f
        }
        vera.VERA_ADDR_H &= 1
    }

    sub set_grayscale(ubyte startindex) {
        ; set 16 consecutive colors to a grayscale gradient from black to white
        internal_set_vera_palette_addr(startindex)
        cx16.r0L=0
        repeat 16 {
            vera.VERA_DATA0 = cx16.r0L
            vera.VERA_DATA0 = cx16.r0L
            cx16.r0L += $11
        }
        vera.VERA_ADDR_H &= 1
    }

    sub color8to4(uword colorpointer) -> uword {
        ; accurately convert 24 bits (3 bytes) RGB color, in that order in memory, to 16 bits $GB;$0R colorvalue
        cx16.r1 = colorpointer
        cx16.r0 = channel8to4(@(cx16.r1))         ; (red)   -> $00:0R
        cx16.r0H = channel8to4(@(cx16.r1+1))<<4   ; (green) -> $G0:0R
        cx16.r0H |= channel8to4(@(cx16.r1+2))     ; (blue)  -> $GB:0R
        return cx16.r0
    }

    sub channel8to4(ubyte channelvalue) -> ubyte {
        ; accurately convert a single 8 bit color channel value to 4 bits,  see https://threadlocalmutex.com/?p=48
        return msb(channelvalue * $000f + 135)
    }

    sub fade_step_multi(ubyte startindex, ubyte endindex, uword target_rgb) -> bool {
        ; Perform one color fade step for multiple consecutive palette entries.
        ;   startindex = palette index of first color to fade
        ;   endindex = palette index of last color to fade
        ;   target_rgb = $RGB color value to fade towards
        ; Returns true if one or more colors were changed, false if no fade steps were done anymore.
        ; So you usually keep calling this until it returns false.
        bool changed = false
        while startindex <= endindex {
            if fade_step(startindex, target_rgb)
                changed=true
            startindex++
            if_z
                break
        }
        return changed
    }

    sub fade_step_colors(ubyte startindex, ubyte endindex, uword target_colors) -> bool {
        ; Perform one color fade step for multiple consecutive palette entries, to different target colors.
        ;   startindex = palette index of first color to fade
        ;   endindex = palette index of last color to fade, inclusive
        ;   target_colors = address of uword $RGB array of colors to fade towards,
        ;                   in *linear* storage format (@nosplit) which is usually how palette blobs are loaded from disk.
        ; Returns true if one or more colors were changed, false if no fade steps were done anymore.
        ; So you usually keep calling this until it returns false.
        bool changed = false
        while startindex <= endindex {
            if fade_step(startindex, peekw(target_colors))
                changed=true
            target_colors += 2
            startindex++
            if_z
                break
        }
        return changed
    }

    sub fade_step(ubyte index, uword target_rgb) -> bool {
        ; Perform one color fade step for a single palette entry.
        ;   index = palette index of the color to fade
        ;   target_rgb = $RGB color value to fade towards
        ; Returns true if the color was changed, false if no fade step was done anymore.
        ; So you usually keep calling this until it returns false.
        uword color = vera_palette.get_color(index)
        cx16.r0L = msb(color)            ; r
        cx16.r1L = lsb(color) >> 4       ; g
        cx16.r2L = lsb(color) & 15       ; b
        cx16.r0H = msb(target_rgb) & 15  ; r2
        cx16.r1H = lsb(target_rgb) >> 4  ; g2
        cx16.r2H = lsb(target_rgb) & 15  ; b2

        ubyte changed

        ; use cmp() + status bits branches, to avoid multiple compares that could be done just once
        cmp(cx16.r0L, cx16.r0H)
        if_ne {
            if_cc
                cx16.r0L++
            else
                cx16.r0L--
            changed++
        }
        cmp(cx16.r1L, cx16.r1H)
        if_ne {
            if_cc
                cx16.r1L++
            else
                cx16.r1L--
            changed++
        }
        cmp(cx16.r2L, cx16.r2H)
        if_ne {
            if_cc
                cx16.r2L++
            else
                cx16.r2L--
            changed++
        }

        vera_palette.set_color(index, mkword(cx16.r0L, cx16.r1L<<4 | cx16.r2L))
        return changed!=0
    }

    sub set_c64pepto() {
        ; set first 16 colors to the "Pepto" PAL commodore-64 palette  http://www.pepto.de/projects/colorvic/
        uword[] @nosplit colors = [
            $000,  ; 0 = black
            $FFF,  ; 1 = white
            $833,  ; 2 = red
            $7cc,  ; 3 = cyan
            $839,  ; 4 = purple
            $5a4,  ; 5 = green
            $229,  ; 6 = blue
            $ef7,  ; 7 = yellow
            $852,  ; 8 = orange
            $530,  ; 9 = brown
            $c67,  ; 10 = light red
            $444,  ; 11 = dark grey
            $777,  ; 12 = medium grey
            $af9,  ; 13 = light green
            $76e,  ; 14 = light blue
            $bbb   ; 15 = light grey
        ]
        set_rgb_nosplit(colors, len(colors), 0)
    }

    sub set_c64ntsc() {
        ; set first 16 colors to a NTSC commodore-64 palette
        uword[] @nosplit colors = [
            $000,   ; 0 = black
            $FFF,   ; 1 = white
            $934,   ; 2 = red
            $9ff,   ; 3 = cyan
            $73f,   ; 4 = purple
            $4b1,   ; 5 = green
            $20c,   ; 6 = blue
            $ee6,   ; 7 = yellow
            $b53,   ; 8 = orange
            $830,   ; 9 = brown
            $f8a,   ; 10 = light red
            $444,   ; 11 = dark grey
            $999,   ; 12 = medium grey
            $9f9,   ; 13 = light green
            $36f,   ; 14 = light blue
            $ccc    ; 15 = light grey
        ]
        set_rgb_nosplit(colors, len(colors), 0)
    }

    sub set_default16() {
        ; set first 16 colors to the defaults on the X16
        ; (doesn't use the rom table so this works on roms older than 49 as well)
        uword[] @nosplit colors = [
            $000,   ; 0 = black
            $fff,   ; 1 = white
            $800,   ; 2 = red
            $afe,   ; 3 = cyan
            $c4c,   ; 4 = purple
            $0c5,   ; 5 = green
            $00a,   ; 6 = blue
            $ee7,   ; 7 = yellow
            $d85,   ; 8 = orange
            $640,   ; 9 = brown
            $f77,   ; 10 = light red
            $333,   ; 11 = dark grey
            $777,   ; 12 = medium grey
            $af6,   ; 13 = light green
            $08f,   ; 14 = light blue
            $bbb    ; 15 = light grey
        ]
        set_rgb_nosplit(colors, len(colors), 0)
    }


    private sub internal_set_vera_palette_addr(ubyte index) {
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_H = %00010001
        vera.VERA_ADDR = $fa00+(index *$0002)
    }
}
