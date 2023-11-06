; Experimental Vera FX support.
; Docs:
; https://github.com/X16Community/x16-docs/blob/master/VERA%20FX%20Reference.md
; https://docs.google.com/document/d/1q34uWOiM3Be2pnaHRVgSdHySI-qsiQWPTo_gfE54PTg/edit

verafx {
    %option no_symbol_prefixing

    sub available() -> bool {
        ; returns true if Vera FX is available (Vera V0.3.1 or later), false if not.
        cx16.r1L = 0
        cx16.r0L = cx16.VERA_CTRL
        cx16.VERA_CTRL = $7e
        if cx16.VERA_DC_VER0 == $56 {
            ; Vera version number is valid.
            ; Vera fx is available on Vera version 0.3.1 and later,
            ; so no need to even check VERA_DC_VER1, which contains 0 (or higher)
            cx16.r1L = mkword(cx16.VERA_DC_VER2, cx16.VERA_DC_VER3) >= $0301
        }
        cx16.VERA_CTRL = cx16.r0L
        return cx16.r1L
    }

    sub clear(ubyte vbank, uword vaddr, ubyte data, uword amountof32bits) {
        ; use cached 4-byte write to quickly clear a portion of the video memory to a given byte value
        ; this routine is around 3 times faster as gfx2.clear_screen()
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

        if (amountof32bits & %1111110000000011) == 0 {
            repeat lsb(amountof32bits >> 2)
                unroll 4 cx16.VERA_DATA0=0       ; write 4 bytes at a time, unrolled
        }
        else if (amountof32bits & %1111111000000001) == 0 {
            repeat lsb(amountof32bits >> 1)
                unroll 2 cx16.VERA_DATA0=0       ; write 4 bytes at a time, unrolled
        }
        else if (lsb(amountof32bits) & 3) == 0 {
            repeat amountof32bits >> 2
                unroll 4 cx16.VERA_DATA0=0       ; write 4 bytes at a time, unrolled
        }
        else if (lsb(amountof32bits) & 1) == 0 {
            repeat amountof32bits >> 1
                unroll 2 cx16.VERA_DATA0=0       ; write 4 bytes at a time, unrolled
        }
        else {
            repeat amountof32bits
                cx16.VERA_DATA0=0       ; write 4 bytes at a time
        }

        cx16.VERA_FX_CTRL = 0       ; cache write disable
        cx16.VERA_CTRL = 0
    }

    ; unsigned multiplication just passes the values as signed to muls
    ; if you do this yourself in your call to muls, it will save a few instructions.
    sub mult(uword value1, uword value2) -> uword {
        ; Returns the lower 16 bits of the 32 bits result,
        ; the upper 16 bits are stored in cx16.r0 so you can access those separately.
        ; It's not part of the subroutine's signature to avoid awkward use of multiple returnvalues.
        return muls(value1 as word, value2 as word) as uword
    }

    asmsub muls(word value1 @R0, word value2 @R1) clobbers(X) -> word @AY {
        ; Returns the lower 16 bits of the 32 bits result in AY,
        ; the upper 16 bits are stored in cx16.r0 so you can access those separately.
        ; It's not part of the subroutine's signature to avoid awkward use of multiple returnvalues.
        %asm {{
            lda  #(2 << 1)
            sta  cx16.VERA_CTRL        ; $9F25
            stz  cx16.VERA_FX_CTRL     ; $9F29 (mainly to reset Addr1 Mode to 0)
            lda  #%00010000
            sta  cx16.VERA_FX_MULT     ; $9F2C
            lda  #(6 << 1)
            sta  cx16.VERA_CTRL        ; $9F25
            lda  cx16.r0
            ldy  cx16.r0+1
            sta  cx16.VERA_FX_CACHE_L  ; $9F29
            sty  cx16.VERA_FX_CACHE_M  ; $9F2A
            lda  cx16.r1
            ldy  cx16.r1+1
            sta  cx16.VERA_FX_CACHE_H  ; $9F2B
            sty  cx16.VERA_FX_CACHE_U  ; $9F2C
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
            lda  cx16.VERA_DATA0
            ldy  cx16.VERA_DATA0
            ldx  cx16.VERA_DATA0      ; store the upper 16 bits of the result in r0
            stx  cx16.r0
            ldx  cx16.VERA_DATA0
            stx  cx16.r0+1
            stz  cx16.VERA_FX_CTRL    ; Cache write disable
            stz  cx16.VERA_CTRL       ; reset DCSEL
            rts
        }}
    }

    sub transparency(bool enable) {
        cx16.VERA_CTRL = 2<<1       ; dcsel = 2
        if enable
            cx16.VERA_FX_CTRL |= %10000000
        else
            cx16.VERA_FX_CTRL &= %01111111
        cx16.VERA_CTRL = 0
    }
}
