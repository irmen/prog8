txt {
    ; the textio functions shared across compiler targets
    %option merge, no_symbol_prefixing, ignore_unused

    asmsub petscii2scr(ubyte petscii_char @A) -> ubyte @A {
        ; -- convert petscii character to screencode
        %asm {{
            sta  P8ZP_SCRATCH_REG
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            tax
            lda  _offsets,x
            eor  P8ZP_SCRATCH_REG
            rts
_offsets    .byte  128, 0, 64, 32, 64, 192, 128, 128
        }}
    }

    asmsub petscii2scr_str(str petscii_string @AY) {
        ; -- convert petscii string to screencodes string
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  +
            jsr  petscii2scr
            sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
+           rts
        }}
}


    sub print_bool(bool value) {
        if value
            txt.print("true")
        else
            txt.print("false")
    }

}
