; Partial Vera FX support:
; - fast 32 bit cached writes (clear, copy)
; - transparent write setting
; - hardware 16 bits multiplications
; - hardware accelerated line drawing (8 bpp screen mode only!)
;
; Docs:
; https://github.com/X16Community/x16-docs/blob/fb63156cca2d6de98be0577aacbe4ddef458f896/X16%20Reference%20-%2010%20-%20VERA%20FX%20Reference.md
; https://docs.google.com/document/d/1q34uWOiM3Be2pnaHRVgSdHySI-qsiQWPTo_gfE54PTg

verafx {
    %option no_symbol_prefixing, ignore_unused

    sub available() -> bool {
        ; returns true if Vera FX is available (Vera V0.3.1 or later), false if not.
        cx16.r0L = cx16.VERA_CTRL
        cx16.r0H = 0
        cx16.VERA_CTRL = $7e
        if cx16.VERA_DC_VER0 == $56 {
            ; Vera version number is valid. Vera fx is available on Vera version 0.3.1 and later.
            if cx16.VERA_DC_VER1>0
                cx16.r0H = 1
            else
                cx16.r0H = mkword(cx16.VERA_DC_VER2, cx16.VERA_DC_VER3) >= $0301 as ubyte
        }
        cx16.VERA_CTRL = cx16.r0L
        return cx16.r0H as bool
    }

    sub clear(ubyte vbank, uword vaddr, ubyte data, uword num_longwords) {
        ; use cached 4-byte write to quickly clear a portion of the video memory to a given byte value
        ; this routine is around 3 times faster as gfx_hires/gfx_lores.clear_screen()
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_H = vbank | %00110000       ; 4-byte increment
        cx16.VERA_ADDR_M = msb(vaddr)
        cx16.VERA_ADDR_L = lsb(vaddr)
        cx16.VERA_CTRL = 6<<1       ; dcsel = 6, fill the 32 bits cache
        cx16.VERA_FX_CACHE_L = data
        cx16.VERA_FX_CACHE_M = data
        cx16.VERA_FX_CACHE_H = data
        cx16.VERA_FX_CACHE_U = data
        cx16.VERA_CTRL = 2<<1       ; dcsel = 2
        cx16.VERA_FX_MULT = 0
        cx16.VERA_FX_CTRL = %01000000    ; cache write enable

        cx16.r0 = num_longwords>>3
        if cx16.r0H==0 {
            repeat cx16.r0L {
                unroll 8 cx16.VERA_DATA0=0       ; write 8*4 bytes at a time, unrolled
            }
        } else {
            repeat cx16.r0 {
                unroll 8 cx16.VERA_DATA0=0       ; write 8*4 bytes at a time, unrolled
            }
        }

        repeat lsb(num_longwords) & 7 {
            cx16.VERA_DATA0=0       ; write 4 bytes at a time (remaining longs)
        }

        cx16.VERA_FX_CTRL = 0       ; cache write disable
        cx16.VERA_CTRL = 0
    }

    sub copy(ubyte srcbank, uword srcaddr, ubyte tgtbank, uword tgtaddr, uword num_longwords) {
        ; use cached 4-byte writes to quickly copy a portion of the video memory to somewhere else
        ; this routine is about 50% faster as a plain byte-by-byte copy
        cx16.VERA_CTRL = 1
        cx16.VERA_ADDR_H = srcbank | %00010000       ; source: 1-byte increment
        cx16.VERA_ADDR_M = msb(srcaddr)
        cx16.VERA_ADDR_L = lsb(srcaddr)
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_H = tgtbank | %00110000       ; target: 4-byte increment
        cx16.VERA_ADDR_M = msb(tgtaddr)
        cx16.VERA_ADDR_L = lsb(tgtaddr)
        cx16.VERA_CTRL = 2<<1       ; dcsel = 2
        cx16.VERA_FX_MULT = 0
        cx16.VERA_FX_CTRL = %01100000    ; cache write enable + cache fill enable

        cx16.r0 = num_longwords>>1

        if cx16.r0H==0 {
            repeat cx16.r0L {
                unroll 2 %asm {{
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    stz  cx16.VERA_DATA0
                }}
            }
        } else {
            repeat cx16.r0 {
                unroll 2 %asm {{
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    stz  cx16.VERA_DATA0
                }}
            }
        }

        if lsb(num_longwords) & 1 == 1 {
            %asm {{
                lda  cx16.VERA_DATA1
                lda  cx16.VERA_DATA1
                lda  cx16.VERA_DATA1
                lda  cx16.VERA_DATA1
                stz  cx16.VERA_DATA0
            }}
        }

        cx16.VERA_FX_CTRL = 0    ; cache write disable
        cx16.VERA_CTRL = 0
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
            sta  cx16.VERA_CTRL        ; $9F25
            stz  cx16.VERA_FX_CTRL     ; $9F29 (mainly to reset Addr1 Mode to 0)
            lda  #%00010000
            sta  cx16.VERA_FX_MULT     ; $9F2C
            lda  #(6 << 1)
            sta  cx16.VERA_CTRL        ; $9F25
            lda  cx16.r0
            sta  cx16.VERA_FX_CACHE_L  ; $9F29
            lda  cx16.r0+1
            sta  cx16.VERA_FX_CACHE_M  ; $9F2A
            lda  cx16.r1
            sta  cx16.VERA_FX_CACHE_H  ; $9F2B
            lda  cx16.r1+1
            sta  cx16.VERA_FX_CACHE_U  ; $9F2C
            lda  cx16.VERA_FX_ACCUM_RESET   ; $9F29 (DCSEL=6)

            ; Set the ADDR0 pointer to $1f9bc and write our multiplication result there
            ; (these are the 4 bytes just before the PSG registers start)
            lda  #(2 << 1)
            sta  cx16.VERA_CTRL
            lda  #%01000000           ; Cache Write Enable
            sta  cx16.VERA_FX_CTRL
            lda  #$bc
            sta  cx16.VERA_ADDR_L
            lda  #$f9
            sta  cx16.VERA_ADDR_M
            lda  #$01
            sta  cx16.VERA_ADDR_H     ; no increment
            stz  cx16.VERA_DATA0      ; multiply and write out result
            lda  #%00010001           ; $01 with Increment 1
            sta  cx16.VERA_ADDR_H     ; so we can read out the result
            lda  cx16.VERA_DATA0      ; store the lower 16 bits of the result in R0
            ldy  cx16.VERA_DATA0
            sta  cx16.r0L
            sty  cx16.r0H
            lda  cx16.VERA_DATA0      ; store the upper 16 bits of the result in R1
            ldy  cx16.VERA_DATA0      ; store the upper 16 bits of the result in R1
            sta  cx16.r1L
            sty  cx16.r1H
            stz  cx16.VERA_FX_CTRL    ; Cache write disable
            stz  cx16.VERA_FX_MULT    ; $9F2C  reset multiply bit
            stz  cx16.VERA_CTRL       ; reset DCSEL
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
            sta  cx16.VERA_CTRL         ; dcsel = 2
            lda  #%00000001
            sta  cx16.VERA_FX_CTRL      ; addr1 mode = line draw helper (8 bpp)
            lda  #(3<<1)
            sta  cx16.VERA_CTRL         ; dcsel = 3
            lda  slope
            sta  cx16.VERA_FX_X_INCR    ; (writing X_INCR also centers the subpixel position and resets overflow)
            lda  slope+1
            sta  cx16.VERA_FX_X_INCR+1
            ; ADDR0 provides the 'sometimes' increment for the helper
            stz  cx16.VERA_CTRL         ; addrsel = 0
            ldx  octant
            lda  sometimes_incr_table,x
            sta  cx16.VERA_ADDR_H
            ; ADDR1 = start pixel, gets the 'always' increment
            lda  #1
            sta  cx16.VERA_CTRL         ; addrsel = 1 (bit 0)
            lda  x1
            sta  cx16.VERA_ADDR_L
            lda  x1+1
            sta  cx16.VERA_ADDR_M
            lda  always_incr_table,x
            sta  cx16.VERA_ADDR_H

            ; add the y-offset to the start address in ADDR1
            ldy  y1
            lda  cx16.VERA_ADDR_L
            clc
            adc  times320_lo,y
            sta  cx16.VERA_ADDR_L
            lda  cx16.VERA_ADDR_M
            adc  times320_mid,y
            sta  cx16.VERA_ADDR_M
            lda  cx16.VERA_ADDR_H
            and  #$01
            adc  times320_hi,y
            sta  P8ZP_SCRATCH_B1
            lda  cx16.VERA_ADDR_H
            and  #$f8
            ora  P8ZP_SCRATCH_B1
            sta  cx16.VERA_ADDR_H

            ; draw the line: first the remainder pixels one at a time, then unrolled 8 pixels at a time
            ldy  remainder_pixels
            beq  +
            lda  color
-           sta  cx16.VERA_DATA1
            dey
            bne  -
+           ldy  full_octets
            beq  _done
            lda  color
-           sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            sta  cx16.VERA_DATA1
            dey
            bne  -
_done
            ; reset the FX registers back to normal
            lda  #(2<<1)
            sta  cx16.VERA_CTRL     ; dcsel = 2
            stz  cx16.VERA_FX_CTRL  ; addr1 mode = normal again
            lda  #1
            sta  cx16.VERA_CTRL     ; addrsel = 1 (bit 0)
            stz  cx16.VERA_ADDR_H   ; reset ADDR1 (DATA1) address increment
            stz  cx16.VERA_CTRL     ; addrsel = 0
            stz  cx16.VERA_ADDR_H   ; reset ADDR0 (DATA0) address increment
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
        cx16.VERA_CTRL = 2<<1       ; dcsel = 2
        if enable
            cx16.VERA_FX_CTRL |= %10000000
        else
            cx16.VERA_FX_CTRL &= %01111111
        cx16.VERA_CTRL = 0
    }
}
