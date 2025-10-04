; Partial Vera FX support:
; - fast 32 bit cached writes (clear, copy)
; - transparent write setting
; - hardware 16 bits multiplications
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

        if (num_longwords & %1111110000000011) == 0 {
            repeat lsb(num_longwords >> 2)
                unroll 4 cx16.VERA_DATA0=0       ; write 4*4 bytes at a time, unrolled
        }
        else if (num_longwords & %1111111000000001) == 0 {
            repeat lsb(num_longwords >> 1)
                unroll 2 cx16.VERA_DATA0=0       ; write 2*4 bytes at a time, unrolled
        }
        else if (lsb(num_longwords) & 3) == 0 {
            repeat num_longwords >> 2
                unroll 4 cx16.VERA_DATA0=0       ; write 4*4 bytes at a time, unrolled
        }
        else if (lsb(num_longwords) & 1) == 0 {
            repeat num_longwords >> 1
                unroll 2 cx16.VERA_DATA0=0       ; write 2*4 bytes at a time, unrolled
        }
        else {
            repeat num_longwords
                cx16.VERA_DATA0=0       ; write 4 bytes at a time
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
        cx16.r0 = num_longwords

        if (cx16.r0L & 1) == 0 {
            repeat cx16.r0>>1 {
                %asm {{
                    lda  cx16.VERA_DATA1    ; fill cache with 4 source bytes...
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    stz  cx16.VERA_DATA0    ; write 4 bytes at once.
                    lda  cx16.VERA_DATA1    ; fill cache with 4 source bytes...
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    stz  cx16.VERA_DATA0    ; write 4 bytes at once.
                }}
            }
        } else {
            repeat cx16.r0 {
                %asm {{
                    lda  cx16.VERA_DATA1    ; fill cache with 4 source bytes...
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    lda  cx16.VERA_DATA1
                    stz  cx16.VERA_DATA0    ; write 4 bytes at once.
                }}
            }
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


    asmsub muls(word value1 @R0, word value2 @R1) clobbers(X) -> long @R0R1_32 {
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
