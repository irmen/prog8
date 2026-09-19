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

verafx {
    ; Partial Vera FX support:
    ; - fast 32 bit cached writes (clear, copy)
    ; - transparent write setting
    ; - hardware 16 bits multiplications
    ; - hardware accelerated line drawing (8 bpp screen mode only!)
    ;
    ; Docs:
    ; https://github.com/X16Community/x16-docs/blob/fb63156cca2d6de98be0577aacbe4ddef458f896/X16%20Reference%20-%2010%20-%20VERA%20FX%20Reference.md
    ; https://docs.google.com/document/d/1q34uWOiM3Be2pnaHRVgSdHySI-qsiQWPTo_gfE54PTg

    %option no_symbol_prefixing, ignore_unused

    sub available() -> bool {
        ; returns true if Vera FX is available (Vera V0.3.1 or later), false if not.
        cx16.r0L = vera.VERA_CTRL
        cx16.r0H = 0
        vera.VERA_CTRL = $7e
        if vera.VERA_DC_VER0 == $56 {
            ; Vera version number is valid. Vera fx is available on Vera version 0.3.1 and later.
            if vera.VERA_DC_VER1>0
                cx16.r0H = 1
            else
                cx16.r0H = mkword(vera.VERA_DC_VER2, vera.VERA_DC_VER3) >= $0301 as ubyte
        }
        vera.VERA_CTRL = cx16.r0L
        return cx16.r0H as bool
    }

    sub clear(ubyte vbank, uword vaddr, ubyte data, uword num_longwords) {
        ; use cached 4-byte write to quickly clear a portion of the video memory to a given byte value
        ; this routine is around 3 times faster as gfx_hires/gfx_lores.clear_screen()
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_H = vbank | %00110000       ; 4-byte increment
        vera.VERA_ADDR_M = msb(vaddr)
        vera.VERA_ADDR_L = lsb(vaddr)
        vera.VERA_CTRL = 6<<1       ; dcsel = 6, fill the 32 bits cache
        vera.VERA_FX_CACHE_L = data
        vera.VERA_FX_CACHE_M = data
        vera.VERA_FX_CACHE_H = data
        vera.VERA_FX_CACHE_U = data
        vera.VERA_CTRL = 2<<1       ; dcsel = 2
        vera.VERA_FX_MULT = 0
        vera.VERA_FX_CTRL = %01000000    ; cache write enable

        cx16.r0 = num_longwords>>3
        if cx16.r0H==0 {
            repeat cx16.r0L {
                unroll 8 vera.VERA_DATA0=0       ; write 8*4 bytes at a time, unrolled
            }
        } else {
            repeat cx16.r0 {
                unroll 8 vera.VERA_DATA0=0       ; write 8*4 bytes at a time, unrolled
            }
        }

        repeat lsb(num_longwords) & 7 {
            vera.VERA_DATA0=0       ; write 4 bytes at a time (remaining longs)
        }

        vera.VERA_FX_CTRL = 0       ; cache write disable
        vera.VERA_CTRL = 0
    }

    sub copy(ubyte srcbank, uword srcaddr, ubyte tgtbank, uword tgtaddr, uword num_longwords) {
        ; use cached 4-byte writes to quickly copy a portion of the video memory to somewhere else
        ; this routine is about 50% faster as a plain byte-by-byte copy
        vera.VERA_CTRL = 1
        vera.VERA_ADDR_H = srcbank | %00010000       ; source: 1-byte increment
        vera.VERA_ADDR_M = msb(srcaddr)
        vera.VERA_ADDR_L = lsb(srcaddr)
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_H = tgtbank | %00110000       ; target: 4-byte increment
        vera.VERA_ADDR_M = msb(tgtaddr)
        vera.VERA_ADDR_L = lsb(tgtaddr)
        vera.VERA_CTRL = 2<<1       ; dcsel = 2
        vera.VERA_FX_MULT = 0
        vera.VERA_FX_CTRL = %01100000    ; cache write enable + cache fill enable

        cx16.r0 = num_longwords>>1

        if cx16.r0H==0 {
            repeat cx16.r0L {
                unroll 2 %asm {{
                    lda  vera.VERA_DATA1
                    lda  vera.VERA_DATA1
                    lda  vera.VERA_DATA1
                    lda  vera.VERA_DATA1
                    lda  #0
                    sta  vera.VERA_DATA0
                }}
            }
        } else {
            repeat cx16.r0 {
                unroll 2 %asm {{
                    lda  vera.VERA_DATA1
                    lda  vera.VERA_DATA1
                    lda  vera.VERA_DATA1
                    lda  vera.VERA_DATA1
                    lda  #0
                    sta  vera.VERA_DATA0
                }}
            }
        }

        if lsb(num_longwords) & 1 == 1 {
            %asm {{
                lda  vera.VERA_DATA1
                lda  vera.VERA_DATA1
                lda  vera.VERA_DATA1
                lda  vera.VERA_DATA1
                lda  #0
                sta  vera.VERA_DATA0
            }}
        }

        vera.VERA_FX_CTRL = 0    ; cache write disable
        vera.VERA_CTRL = 0
    }


    asmsub mult16(uword value1 @R0, uword value2 @R1) clobbers(X) -> uword @AY {
        ; Returns the lower 16 bits unsigned result of R0*R1 in AY
        ; Note: only the lower 16 bits!   (the upper 16 bits are not valid for unsigned word multiplications, only for signed)
        ; Verafx doesn't support unsigned values like this for full 32 bit result.
        ; Note: clobbers VRAM $1f9bc - $1f9bf (inclusive)
        %asm {{
            jmp  muls16
        }}
    }

    asmsub muls16(word value1 @R0, word value2 @R1) clobbers(X) -> word @AY {
        ; Returns just the lower 16 bits signed result of the multiplication in cx16.AY.
        ; Note: clobbers R0, R1, and VRAM $1f9bc - $1f9bf (inclusive)
        %asm {{
            jsr  muls
            lda  cx16.r0L
            ldy  cx16.r0H
            rts
        }}
    }


    asmsub muls(word value1 @R0, word value2 @R1) clobbers(X) -> long @R0R1 {
        ; Returns the 32 bits signed result in R0:R1  (lower word, upper word).
        ; Vera Fx multiplication support only works on signed values!
        ; Note: clobbers VRAM $1f9bc - $1f9bf (inclusive)
        %asm {{
            lda  #(2 << 1)
            sta  vera.VERA_CTRL        ; $9F25
            lda  #0
            sta  vera.VERA_FX_CTRL     ; $9F29 (mainly to reset Addr1 Mode to 0)
            lda  #%00010000
            sta  vera.VERA_FX_MULT     ; $9F2C
            lda  #(6 << 1)
            sta  vera.VERA_CTRL        ; $9F25
            lda  cx16.r0
            sta  vera.VERA_FX_CACHE_L  ; $9F29
            lda  cx16.r0+1
            sta  vera.VERA_FX_CACHE_M  ; $9F2A
            lda  cx16.r1
            sta  vera.VERA_FX_CACHE_H  ; $9F2B
            lda  cx16.r1+1
            sta  vera.VERA_FX_CACHE_U  ; $9F2C
            lda  vera.VERA_FX_ACCUM_RESET   ; $9F29 (DCSEL=6)

            ; Set the ADDR0 pointer to $1f9bc and write our multiplication result there
            ; (these are the 4 bytes just before the PSG registers start)
            lda  #(2 << 1)
            sta  vera.VERA_CTRL
            lda  #%01000000           ; Cache Write Enable
            sta  vera.VERA_FX_CTRL
            lda  #$bc
            sta  vera.VERA_ADDR_L
            lda  #$f9
            sta  vera.VERA_ADDR_M
            lda  #$01
            sta  vera.VERA_ADDR_H     ; no increment
            lda  #0
            sta  vera.VERA_DATA0      ; multiply and write out result
            lda  #%00010001           ; $01 with Increment 1
            sta  vera.VERA_ADDR_H     ; so we can read out the result
            lda  vera.VERA_DATA0      ; store the lower 16 bits of the result in R0
            ldy  vera.VERA_DATA0
            sta  cx16.r0L
            sty  cx16.r0H
            lda  vera.VERA_DATA0      ; store the upper 16 bits of the result in R1
            ldy  vera.VERA_DATA0      ; store the upper 16 bits of the result in R1
            sta  cx16.r1L
            sty  cx16.r1H
            lda  #0
            sta  vera.VERA_FX_CTRL    ; Cache write disable
            sta  vera.VERA_FX_MULT    ; $9F2C  reset multiply bit
            sta  vera.VERA_CTRL       ; reset DCSEL
            rts
        }}
    }

    sub line(uword x1, ubyte y1, uword x2, ubyte y2, ubyte color) {
        ; Use the Vera FX line draw helper to draw a line very fast in a 320x240 256 color (8 bpp) bitmap screen
        ; (the default cx16 screen mode 128, as used by the gfx_lores module, with the bitmap at vram address 0).
        ; WARNING: ONLY WORKS IN 8 BPP SCREEN MODE! The helper has a hardware bug in 4 bpp mode.
        ; No bounds checking or clipping is done, all coordinates must lie within the screen (0..319, 0..239).
        ; Also resets the address increments of DATA0 and DATA1 to 0 afterwards.
        ; The line is always drawn from top to bottom (y1<=y2 after sorting), this avoids the negative
        ; (decrement) y-increments that the helper handles poorly. x can go either left or right.
        ubyte @zp octant
        uword @zp dx
        uword @zp dy
        if y1>y2 {
            cx16.r0 = x1
            x1 = x2
            x2 = cx16.r0
            octant = y1
            y1 = y2
            y2 = octant
        }
        dy = y2
        dy -= y1
        uword @zp length
        if x2>=x1 {
            dx = x2-x1
            length = dx
            octant = 0              ; x goes right
        } else {
            dx = x1-x2
            length = dx
            octant = 1              ; x goes left
        }
        if dy>length {
            octant |= 2
            length = dy
            dy = dx
        }
        ; slope in 0.9 fixed point format for the FX increment register (1.0 = $200), rounded to nearest.
        ; Computed as (dy/length in 0.8 fixed point) << 1, which stays within 16 bits:
        ; dy<=239 so dy<<8 <= 61240, and dy<=length so the quotient is <=256, doubled to <=512.
        uword slope = 0
        if length!=0 {
            slope = (((dy << 8) + (length>>1)) / length) << 1
        }
        length++
        ubyte remainder_pixels = lsb(length) & 7
        ubyte full_octets = lsb(length>>3)

        ; 4 "octants" (y1<=y2 always after the sorting above, so the line always goes down):
        ;   octant 0 = right/down, shallow slope (dx>=dy): always step +1 in x, sometimes step +320 in y
        ;   octant 1 = left/down, shallow slope:           always step -1 in x, sometimes step +320 in y
        ;   octant 2 = right/down, steep slope (dy>dx):    always step +320 in y, sometimes step +1 in x
        ;   octant 3 = left/down, steep slope:             always step +320 in y, sometimes step -1 in x
        ; address increment values: +1 = $10, -1 = $18 (decrement), +320 = $e0
        ubyte[4] @shared always_incr_table = [ $10, $18, $e0, $e0 ]
        ubyte[4] @shared sometimes_incr_table = [ $e0, $e0, $10, $18 ]

        %asm {{
            ; set up the FX line draw helper and the start address in ADDR1
            lda  #(2<<1)
            sta  vera.VERA_CTRL         ; dcsel = 2
            lda  #%00000001
            sta  vera.VERA_FX_CTRL      ; addr1 mode = line draw helper (8 bpp)
            lda  #(3<<1)
            sta  vera.VERA_CTRL         ; dcsel = 3
            lda  slope
            sta  vera.VERA_FX_X_INCR    ; (writing X_INCR also centers the subpixel position and resets overflow)
            lda  slope+1
            sta  vera.VERA_FX_X_INCR+1
            ; ADDR0 provides the 'sometimes' increment for the helper
            lda  #0
            sta  vera.VERA_CTRL         ; addrsel = 0
            ldx  octant
            lda  sometimes_incr_table,x
            sta  vera.VERA_ADDR_H
            ; ADDR1 = start pixel, gets the 'always' increment
            lda  #1
            sta  vera.VERA_CTRL         ; addrsel = 1 (bit 0)
            lda  x1
            sta  vera.VERA_ADDR_L
            lda  x1+1
            sta  vera.VERA_ADDR_M
            lda  always_incr_table,x
            sta  vera.VERA_ADDR_H

            ; add the y-offset to the start address in ADDR1
            ldy  y1
            lda  vera.VERA_ADDR_L
            clc
            adc  times320_lo,y
            sta  vera.VERA_ADDR_L
            lda  vera.VERA_ADDR_M
            adc  times320_mid,y
            sta  vera.VERA_ADDR_M
            lda  vera.VERA_ADDR_H
            and  #$01
            adc  times320_hi,y
            sta  P8ZP_SCRATCH_B1
            lda  vera.VERA_ADDR_H
            and  #$f8
            ora  P8ZP_SCRATCH_B1
            sta  vera.VERA_ADDR_H

            ; draw the line: first the remainder pixels one at a time, then unrolled 8 pixels at a time
            ldy  remainder_pixels
            beq  +
            lda  color
-           sta  vera.VERA_DATA1
            dey
            bne  -
+           ldy  full_octets
            beq  _done
            lda  color
-           sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            sta  vera.VERA_DATA1
            dey
            bne  -
_done
            ; reset the FX registers back to normal
            lda  #(2<<1)
            sta  vera.VERA_CTRL     ; dcsel = 2
            ldx  #0
            stx  vera.VERA_FX_CTRL  ; addr1 mode = normal again
            lda  #1
            sta  vera.VERA_CTRL     ; addrsel = 1 (bit 0)
            stx  vera.VERA_ADDR_H   ; reset ADDR1 (DATA1) address increment
            stx  vera.VERA_CTRL     ; addrsel = 0
            stx  vera.VERA_ADDR_H   ; reset ADDR0 (DATA0) address increment
            rts

            ; multiplication by 320 lookup table (used to add the y-offset to the start address above)
times320 := 320*range(240)
times320_lo     .byte <times320
times320_mid    .byte >times320
times320_hi     .byte `times320
        }}
    }

    sub transparency(bool enable) {
        ; Set transparent write mode for VeraFX cached writes and also for normal writes to DATA0/DATA.
        ; If enabled, pixels with value 0 do not modify VRAM when written (so they are "transparent")
        vera.VERA_CTRL = 2<<1       ; dcsel = 2
        if enable
            vera.VERA_FX_CTRL |= %10000000
        else
            vera.VERA_FX_CTRL &= %01111111
        vera.VERA_CTRL = 0
    }
}
