; Experimental Vera FX support.
; Docs:
; https://github.com/X16Community/x16-docs/blob/master/VERA%20FX%20Reference.md
; https://docs.google.com/document/d/1q34uWOiM3Be2pnaHRVgSdHySI-qsiQWPTo_gfE54PTg/edit

verafx {
    %option no_symbol_prefixing

    ; unsigned multiplication just passes the values as signed to muls
    ; if you do this yourself in your call to muls, it will save a few instructions.
    sub mult(uword value1, uword value2) -> uword {
        return muls(value1 as word, value2 as word) as uword
    }

    asmsub muls(word value1 @R0, word value2 @R1) -> word @AY {
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
            stz  cx16.VERA_FX_CTRL    ; Cache write disable
            lda  cx16.VERA_DATA0
            ldy  cx16.VERA_DATA0
            rts
; we skip the upper 16 bits of the result:
;            lda  cx16.VERA_DATA0
;            sta  $0402
;            lda  cx16.VERA_DATA0
;            sta  $0403
        }}
    }
}
