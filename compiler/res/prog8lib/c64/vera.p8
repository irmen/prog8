%import syslib
%import buffers

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

vera_gfx {
    ; optimized graphics routines for just the single screen mode: lores 320*240, 256c  (8bpp)
    ; bitmap image needs to start at VRAM address $00000.

    %option ignore_unused

    const uword WIDTH = 320
    const ubyte HEIGHT = 240

    sub init() {
        ; 320x240 8bpp bitmap on layer 1, bitmap at vram $00000, set via direct vera register pokes
        vera.VERA_CTRL = 0
        vera.VERA_DC_VIDEO = (vera.VERA_DC_VIDEO & %11001100) | %00100001     ; layer 1 only, force VGA output (a vera reset leaves output mode 0=disabled)
        vera.VERA_DC_HSCALE = 64
        vera.VERA_DC_VSCALE = 64
        vera.VERA_L1_CONFIG = %00000111     ; bitmap mode, 8 bpp
        vera.VERA_L1_MAPBASE = 0
        vera.VERA_L1_TILEBASE = 0           ; bitmap base $00000
        vera.VERA_L1_HSCROLL = 0
        vera.VERA_L1_VSCROLL = 0

        clear_screen(0)

        copy_petscii_charset()

        drawmode_eor(false)
    }

    sub copy_petscii_charset() {
        ; copy the c64 petscii charset (all 256 characters) from the CHARGEN rom to Vera VRAM at $1f000 - $1f800
        ; this makes text() work; the charset is stored at vram bank 1, address $f000 (= $1f000)
        ; (a temporary ram copy must be made first, because the vera registers are inaccessible while the chargen rom is mapped in)
        uword chargen_copy = memory("vera_chargen_copy", 256*8, 0)
        sys.set_irqd()
        c64.banks(%011)             ; enable CHAREN, so the character rom is visible at $d000
        sys.memcopy($d000, chargen_copy, 256*8)
        c64.banks(%111)             ; enable I/O (and thus the vera) again
        sys.clear_irqd()

        vera.vaddr(1, $f000, 0, 1)      ; set up the vera data0 address with auto increment of 1 (so we can just write data0)
        repeat 256*8 {
            vera.VERA_DATA0 = @(chargen_copy)
            chargen_copy++
        }
    }

    sub drawmode_eor(bool enabled) {
        ; with EOR drawing mode you can have non destructive drawing (2*EOR=restore original)
        eor_mode = enabled
    }

    private bool eor_mode

    sub clear_screen(ubyte color) {
        if verafx.available() {
            ; use verafx cache writes to quickly clear the screen
            const ubyte vbank = 0
            const uword vaddr = 0
            vera.VERA_CTRL = 0
            vera.VERA_ADDR_H = vbank | %00110000       ; 4-byte increment
            vera.VERA_ADDR_M = msb(vaddr)
            vera.VERA_ADDR_L = lsb(vaddr)
            vera.VERA_CTRL = 6<<1       ; dcsel = 6, fill the 32 bits cache
            vera.VERA_FX_CACHE_L = color
            vera.VERA_FX_CACHE_M = color
            vera.VERA_FX_CACHE_H = color
            vera.VERA_FX_CACHE_U = color
            vera.VERA_CTRL = 2<<1       ; dcsel = 2
            vera.VERA_FX_MULT = 0
            vera.VERA_FX_CTRL = %01000000    ; cache write enable
            repeat 320/4/4 {
                %asm {{
                    ldy  #240
                    lda  #0
-                   sta  vera.VERA_DATA0
                    sta  vera.VERA_DATA0
                    sta  vera.VERA_DATA0
                    sta  vera.VERA_DATA0
                    dey
                    bne  -
                }}
            }
            vera.VERA_FX_CTRL = 0       ; cache write disable
            vera.VERA_CTRL = 0
            return
        }
        ; fallback to cpu clear
        vera.VERA_CTRL=0
        vera.VERA_ADDR=0
        vera.VERA_ADDR_H = 1<<4    ; 1 pixel auto increment
        repeat HEIGHT {
            %asm {{
                lda  p8v_color
                ldy  #p8c_WIDTH/8
-               .rept 8
                sta  vera.VERA_DATA0
                .endrept
                dey
                bne  -
            }}
        }
        vera.VERA_ADDR=0
        vera.VERA_ADDR_H = 0
    }

    sub rect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        if rwidth==0 or rheight==0
            return
        horizontal_line(xx, yy, rwidth, color)
        if rheight==1
            return
        horizontal_line(xx, yy+rheight-1, rwidth, color)
        vertical_line(xx, yy+1, rheight-2, color)
        if rwidth==1
            return
        vertical_line(xx+rwidth-1, yy+1, rheight-2, color)
    }

    sub safe_rect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        ; does bounds checking and clipping
        safe_horizontal_line(xx, yy, rwidth, color)
        if rheight==1
            return
        uword bottomyy = yy as uword + rheight -1
        if bottomyy<HEIGHT
            safe_horizontal_line(xx, lsb(bottomyy), rwidth, color)
        safe_vertical_line(xx, yy+1, rheight-2, color)
        if rwidth==1
            return
        safe_vertical_line(xx+rwidth-1, yy+1, rheight-2, color)
    }

    sub fillrect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        ; Draw a filled rectangle of the given size and color.
        ; To fill the whole screen, use clear_screen(color) instead - it is much faster.
        if rwidth==0
            return
        repeat rheight {
            horizontal_line(xx, yy, rwidth, color)
            yy++
        }
    }

    sub safe_fillrect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        ; Draw a filled rectangle of the given size and color.
        ; To fill the whole screen, use clear_screen(color) instead - it is much faster.
        ; This safe version does bounds checking and clipping.
        if xx>=WIDTH or yy>=HEIGHT
            return
        if msb(xx)&$80!=0 {
            rwidth += xx
            xx = 0
        }
        if xx>=WIDTH
            return
        if xx+rwidth>WIDTH
            rwidth = WIDTH-xx
        if rwidth>WIDTH
            return

        if yy as uword + rheight > HEIGHT
            rheight = HEIGHT-yy
        if rheight>HEIGHT
            return

        repeat rheight {
            horizontal_line(xx, yy, rwidth, color)
            yy++
        }
    }

    sub horizontal_line(uword xx, ubyte yy, uword length, ubyte color) {
        if length==0
            return
        position(xx, yy)
        ; set vera auto-increment to 1 pixel
        vera.VERA_ADDR_H = vera.VERA_ADDR_H & %00000111 | (1<<4)
        if eor_mode {
            vera.vaddr_clone(0)      ; also setup port 1, for reading
            %asm {{
                ldx  p8v_length+1
                beq  +
                ldy  #0
-               lda  p8v_color
                eor  vera.VERA_DATA1
                sta  vera.VERA_DATA0
                iny
                bne  -
                dex
                bne  -
+               ldy  p8v_length     ; remaining
                beq  +
-               lda  p8v_color
                eor  vera.VERA_DATA1
                sta  vera.VERA_DATA0
                dey
                bne  -
+
            }}
        } else {
            %asm {{
                lda  p8v_color
                ldx  p8v_length+1
                beq  +
                ldy  #0
-               sta  vera.VERA_DATA0
                iny
                bne  -
                dex
                bne  -
+               ldy  p8v_length     ; remaining
                beq  +
-               sta  vera.VERA_DATA0
                dey
                bne  -
+
            }}
        }
    }

    sub safe_horizontal_line(uword xx, ubyte yy, uword length, ubyte color) {
        ; does bounds checking and clipping
        if yy>=HEIGHT
            return
        if msb(xx)&$80!=0 {
            length += xx
            xx = 0
        }
        if xx>=WIDTH
            return
        if xx+length>WIDTH
            length = WIDTH-xx
        if length>WIDTH
            return

        horizontal_line(xx, yy, length, color)
    }

    sub vertical_line(uword xx, ubyte yy, ubyte lheight, ubyte color) {
        if lheight==0
            return
        position(xx, yy)
        ; set vera auto-increment to 320 pixel increment (=next line)
        vera.VERA_ADDR_H = vera.VERA_ADDR_H & %00000111 | (14<<4)
        if eor_mode {
            vera.vaddr_clone(0)      ; also setup port 1, for reading
            %asm {{
                ldy  p8v_lheight
                beq  +
-               lda  p8v_color
                eor  vera.VERA_DATA1
                sta  vera.VERA_DATA0
                dey
                bne  -
+
            }}
        } else {
            %asm {{
                ldy  p8v_lheight
                lda  p8v_color
-               sta  vera.VERA_DATA0
                dey
                bne  -
            }}
        }
    }

    sub safe_vertical_line(uword xx, ubyte yy, ubyte lheight, ubyte color) {
        ; does bounds checking and clipping
        if yy>=HEIGHT
            return
        if msb(xx)&$80!=0 or xx>=WIDTH
            return
        if yy as uword + lheight > HEIGHT
            lheight = HEIGHT-yy
        if lheight>HEIGHT
            return

        vertical_line(xx, yy, lheight, color)
    }

    sub line(uword x1, ubyte y1, uword x2, ubyte y2, ubyte color) {
        ; Bresenham algorithm.
        ; This code special-cases various quadrant loops to allow simple ++ and -- operations.
        ; NOTE:  this is about twice as fast as the kernal routine GRAPH_draw_line
        ;        it trades memory for speed (uses inline plot routine and multiplication lookup tables)
        ;
        ; NOTE:  is currently still a regular 6502 routine, could likely be made much faster with the VeraFX line helper.

        cx16.r4L = color   ; cache color in r4L for internal_line_plot
        cx16.r3L = y2    ; ensure zeropage
        cx16.r1L = y1    ; ensure zeropage

        if cx16.r1L > cx16.r3L {
            ; make sure dy is always positive to have only 4 instead of 8 special cases
            cx16.r0 = x1
            x1 = x2
            x2 = cx16.r0
            cx16.r0L = cx16.r1L
            cx16.r1L = cx16.r3L
            cx16.r3L = cx16.r0L
        }
        word @zp dx = x2 as word
        word @zp dy = cx16.r3L
        dx -= x1
        dy -= cx16.r1L

        if dx==0 {
            vertical_line(x1, cx16.r1L, lsb(dy)+1, color)
            return
        }
        if dy==0 {
            if x1>x2
                x1=x2
            horizontal_line(x1, cx16.r1L, abs(dx) as uword +1, color)
            return
        }

        bool positive_ix = true
        if dx < 0 {
            dx = -dx
            positive_ix = false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2
        word @zp d        ; error term (initialized below based on shallow/steep)

        cx16.r0  = x1    ; ensure zeropage
        cx16.r2  = x2    ; ensure zeropage

        vera.VERA_CTRL = 0
        if dx >= dy {
            d = dx >> 1   ; Initialize error to DX/2 for shallow lines
            if positive_ix {
                repeat {
                    internal_line_plot()
                    if cx16.r0==cx16.r2
                        return
                    cx16.r0++
                    d += dy2
                    if d > dx {
                        cx16.r1L++
                        d -= dx2
                    }
                }
            } else {
                repeat {
                    internal_line_plot()
                    if cx16.r0==cx16.r2
                        return
                    cx16.r0--
                    d += dy2
                    if d > dx {
                        cx16.r1L++
                        d -= dx2
                    }
                }
            }
        }
        else {
            d = dy >> 1   ; Initialize error to DY/2 for steep lines
            if positive_ix {
                repeat {
                    internal_line_plot()
                    if cx16.r1L == cx16.r3L
                        return
                    cx16.r1L++
                    d += dx2
                    if d > dy {
                        cx16.r0++
                        d -= dy2
                    }
                }
            } else {
                repeat {
                    internal_line_plot()
                    if cx16.r1L == cx16.r3L
                        return
                    cx16.r1L++
                    d += dx2
                    if d > dy {
                        cx16.r0--
                        d -= dy2
                    }
                }
            }
        }
    }

    private asmsub internal_line_plot() {
        ; Internal plot routine for line algorithm.
        ; Uses: x in cx16.r0, y in cx16.r1L, color in cx16.r4L
        ; Checks eor_mode flag for XOR vs normal drawing.
        %asm {{
            ldy  cx16.r1L
            clc
            lda  times320_lo,y
            adc  cx16.r0L
            sta  vera.VERA_ADDR_L
            lda  times320_mid,y
            adc  cx16.r0H
            sta  vera.VERA_ADDR_M
            lda  #0
            adc  times320_hi,y
            sta  vera.VERA_ADDR_H

            lda  p8v_eor_mode
            bne  +
            lda  cx16.r4L
            sta  vera.VERA_DATA0
            rts
+           lda  cx16.r4L
            eor  vera.VERA_DATA0
            sta  vera.VERA_DATA0
            rts
        }}
    }

    sub circle(uword @zp xcenter, ubyte @zp ycenter, ubyte radius, ubyte color) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm.
        if radius==0
            return

        ubyte @zp xx = radius
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-xx
        ; R14 = internal plot X
        ; R15 = internal plot Y

        while xx>=yy {
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter + yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter - yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter + xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter - xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()

            yy++
            if decisionOver2>=0 {
                xx--
                decisionOver2 -= xx*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }

        sub plotq() {
            ; cx16.r14 = x, cx16.r15 = y, color=color.
            plot(cx16.r14, cx16.r15L, color)
        }
    }

    sub safe_circle(uword @zp xcenter, uword @zp ycenter, ubyte radius, ubyte color) {
        ; This version does bounds checks and clipping, but is a lot slower.
        ; Midpoint algorithm.
        if radius==0
            return

        ubyte @zp xx = radius
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-xx
        ; R14 = internal plot X
        ; R15 = internal plot Y

        while xx>=yy {
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter + yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter - yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter + xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter - xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()

            yy++
            if decisionOver2>=0 {
                xx--
                decisionOver2 -= xx*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }

        sub plotq() {
            ; cx16.r14 = x, cx16.r15 = y, color=color.
            if cx16.r15 < HEIGHT
                safe_plot(cx16.r14, cx16.r15L, color)
        }
    }

    sub disc(uword @zp xcenter, ubyte @zp ycenter, ubyte @zp radius, ubyte color) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius
        ubyte pendingRadius
        ubyte pendingWidth
        bool hasPending = false
        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, color)
            horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, color)
            if hasPending and pendingRadius != radius {
                if pendingRadius != pendingWidth {
                    horizontal_line(xcenter-pendingWidth, ycenter+pendingRadius, pendingWidth*$0002+1, color)
                    horizontal_line(xcenter-pendingWidth, ycenter-pendingRadius, pendingWidth*$0002+1, color)
                }
                hasPending = false
            }
            if not hasPending {
                pendingRadius = radius
                hasPending = true
            }
            pendingWidth = yy
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
        if hasPending and pendingRadius != pendingWidth {
            horizontal_line(xcenter-pendingWidth, ycenter+pendingRadius, pendingWidth*$0002+1, color)
            horizontal_line(xcenter-pendingWidth, ycenter-pendingRadius, pendingWidth*$0002+1, color)
        }
    }

    sub safe_disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, ubyte color) {
        ; This version does bounds checks and clipping, but is a lot slower.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius
        ubyte pendingRadius
        ubyte pendingWidth
        bool hasPending = false

        while radius>=yy {
            uword liney = ycenter+yy
            if msb(liney)==0
                safe_horizontal_line(xcenter-radius, lsb(liney), radius*$0002+1, color)
            liney = ycenter-yy
            if msb(liney)==0
                safe_horizontal_line(xcenter-radius, lsb(liney), radius*$0002+1, color)

            if hasPending and pendingRadius != radius {
                if pendingRadius != pendingWidth {
                    liney = ycenter+pendingRadius
                    if msb(liney)==0
                        safe_horizontal_line(xcenter-pendingWidth, lsb(liney), pendingWidth*$0002+1, color)
                    liney = ycenter-pendingRadius
                    if msb(liney)==0
                        safe_horizontal_line(xcenter-pendingWidth, lsb(liney), pendingWidth*$0002+1, color)
                }
                hasPending = false
            }
            if not hasPending {
                pendingRadius = radius
                hasPending = true
            }
            pendingWidth = yy

            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
        if hasPending and pendingRadius != pendingWidth {
            uword flushLiney = ycenter+pendingRadius
            if msb(flushLiney)==0
                safe_horizontal_line(xcenter-pendingWidth, lsb(flushLiney), pendingWidth*$0002+1, color)
            flushLiney = ycenter-pendingRadius
            if msb(flushLiney)==0
                safe_horizontal_line(xcenter-pendingWidth, lsb(flushLiney), pendingWidth*$0002+1, color)
        }
    }

    asmsub plot(uword x @AX, ubyte y @Y, ubyte color @R0) {
        ; x in r0,  y in r1,   color.
        %asm {{
            clc
            adc  times320_lo,y
            sta  vera.VERA_ADDR_L
            txa
            adc  times320_mid,y
            sta  vera.VERA_ADDR_M
            lda  #0
            adc  times320_hi,y
            sta  vera.VERA_ADDR_H

            lda  p8v_eor_mode
            bne  +
            lda  cx16.r0L
            sta  vera.VERA_DATA0
            rts
+           lda  cx16.r0L
            eor  vera.VERA_DATA0
            sta  vera.VERA_DATA0
            rts
        }}
    }

    sub safe_plot(uword xx, ubyte yy, ubyte color) {
        ; A plot that does bounds checks to see if the pixel is inside the screen.
        if msb(xx)&$80!=0
            return
        if xx >= WIDTH or yy >= HEIGHT
            return
        plot(xx, yy, color)
    }

    asmsub pget(uword x @AX, ubyte y @Y) -> ubyte @A {
        ; returns the color of the pixel
        %asm {{
            jsr  p8s_position
            lda  vera.VERA_DATA0
            rts
        }}
    }

    sub fill(uword x, ubyte y, ubyte new_color, ubyte stack_rambank) {
        ; reuse a few virtual registers in ZP for variables
        &ubyte fillm = &cx16.r7L
        &ubyte seedm = &cx16.r8L
        &ubyte cmask = &cx16.r8H
        &ubyte vub   = &cx16.r13L
        &ubyte nvub  = &cx16.r13H
        ubyte[4] amask = [$c0,$30,$0c,$03] ; array of cmask bytes

        ; Non-recursive scanline flood fill.
        ; based loosely on code found here https://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm
        ; with the fixes applied to the seedfill_4 routine as mentioned in the comments.
        ; Also see https://lodev.org/cgtutor/floodfill.html
        word @zp xx = x as word
        word @zp yy = y as word
        word x1
        word x2
        byte dy
        cx16.r10L = new_color
        stack.init()

        sub push_stack(word sxl, word sxr, word sy, byte sdy) {
            cx16.r0s = sy+sdy
            if cx16.r0s>=0 and cx16.r0s<=HEIGHT-1 {
                stack.push_w(sxl as uword)
                stack.push_w(sxr as uword)
                stack.push_w(sy as uword)
                stack.push_b(sdy as ubyte)
            }
        }
        sub pop_stack() {
            dy = stack.pop_b() as byte
            yy = stack.pop_w() as word
            x2 = stack.pop_w() as word
            x1 = stack.pop_w() as word
            yy+=dy
        }
        cx16.r11L = pget(xx as uword, lsb(yy))        ; old_color
        if cx16.r11L == cx16.r10L
            return
        if xx<0 or xx>WIDTH-1 or yy<0 or yy>HEIGHT-1
            return
        push_stack(xx, xx, yy, 1)
        push_stack(xx, xx, yy + 1, -1)
        word left = 0
        while not stack.isempty() {
            pop_stack()
            xx = x1
            if fill_scanline_left_8bpp() goto skip
            left = xx + 1
            if left < x1
                push_stack(left, x1 - 1, yy, -dy)
            xx = x1 + 1

            do {
                fill_scanline_right_8bpp()
                push_stack(left, xx - 1, yy, dy)
                if xx > x2 + 1
                    push_stack(x2 + 1, xx - 1, yy, -dy)
skip:
                xx++
                while xx <= x2 {
                    if pget(xx as uword, lsb(yy)) == cx16.r11L
                        break
                    xx++
                }
                left = xx
            } until xx>x2
        }

        sub set_vera_address(bool decr) {
            ; set both data0 and data1 addresses
            position(xx as uword, lsb(yy))
            cx16.r0 = vera.VERA_ADDR
            cx16.r1L = vera.VERA_ADDR_H & 1 | if decr %00011000 else %00010000
            vera.VERA_ADDR_H = cx16.r1L
            vera.VERA_CTRL = 1
            vera.VERA_ADDR = cx16.r0
            vera.VERA_ADDR_H = cx16.r1L
            vera.VERA_CTRL = 0
        }

        sub fill_scanline_left_8bpp() -> bool {
            set_vera_address(true)
            cx16.r9s = xx
            while xx >= 0 {
                if vera.VERA_DATA0 != cx16.r11L
                    break
                vera.VERA_DATA1 = cx16.r10L
                xx--
            }
            return xx==cx16.r9s
        }

        sub fill_scanline_right_8bpp() {
            set_vera_address(false)
            while xx <= WIDTH-1 {
                if vera.VERA_DATA0 != cx16.r11L
                    break
                vera.VERA_DATA1 = cx16.r10L
                xx++
            }
        }
    }

    private const ubyte charset_bank = $1
    private const uword charset_addr = $f000       ; in bank 1, so $1f000

    sub text(uword @zp xx, ubyte yy, ubyte color, str textptr) {
        ; -- Write some text at the given pixel position. The text string must be in an encoding approprite for the charset.
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        uword chardataptr
        ubyte[8] @shared char_bitmap_bytes_left
        ubyte[8] @shared char_bitmap_bytes_right

        while @(textptr)!=0 {
            chardataptr = charset_addr + (@(textptr) as uword)*8
            vera.vaddr(charset_bank, chardataptr, 1, 1)
            repeat 8 {
                position(xx,lsb(yy))
                yy++
                %asm {{
                    ldx  p8v_color
                    lda  vera.VERA_DATA1
                    sta  P8ZP_SCRATCH_B1
                    ldy  #8
-                   asl  P8ZP_SCRATCH_B1
                    bcc  +
                    stx  vera.VERA_DATA0    ; write a pixel
                    bcs  ++
+                   lda  vera.VERA_DATA0    ; don't write a pixel, but do advance to the next address
+                   dey
                    bne  -
                }}
            }
            xx+=8
            yy-=8
            textptr++
        }
    }

    asmsub position(uword x @AX, ubyte y @Y) {
        %asm {{
            clc
            adc  times320_lo,y
            sta  vera.VERA_ADDR_L
            txa
            adc  times320_mid,y
            sta  vera.VERA_ADDR_M
            lda  #%00010000         ; auto increment on
            adc  times320_hi,y
            sta  vera.VERA_ADDR_H
            rts
        }}
    }

    inline asmsub next_pixel(ubyte color @A) {
        ; -- sets the next pixel byte to the graphics chip.
        ;    for 8 bpp screens this will plot 1 pixel.
        ;    for 2 bpp screens it will plot 4 pixels at once (color = bit pattern).
        %asm {{
            sta  vera.VERA_DATA0
        }}
    }

    asmsub next_pixels(uword pixels @AY, uword amount @R0) clobbers(A, X, Y)  {
        ; -- sets the next bunch of pixels from a prepared array of bytes.
        ;    for 8 bpp screens this will plot 1 pixel per byte.
        ;    for 2 bpp screens it will plot 4 pixels at once (colors are the bit patterns per byte).
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldx  cx16.r0+1
            beq  +
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  vera.VERA_DATA0
            iny
            bne  -
            inc  P8ZP_SCRATCH_W1+1       ; next page of 256 pixels
            dex
            bne  -

+           ldx  cx16.r0           ; remaining pixels
            beq  +
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  vera.VERA_DATA0
            iny
            dex
            bne  -
+           rts
        }}
    }

    asmsub set_8_pixels_from_bits(ubyte bits @R0, ubyte oncolor @A, ubyte offcolor @Y) clobbers(X) {
        ; this is only useful in 256 color mode where one pixel equals one byte value.
        %asm {{
            ldx  #8
-           asl  cx16.r0
            bcc  +
            sta  vera.VERA_DATA0
            bcs  ++
+           sty  vera.VERA_DATA0
+           dex
            bne  -
            rts
        }}
    }

    %asm {{
; multiplication by 320 lookup table
times320 := 320*range(240)

times320_lo     .byte <times320
times320_mid    .byte >times320
times320_hi     .byte `times320
    }}
}
