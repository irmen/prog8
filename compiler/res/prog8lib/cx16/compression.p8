; **experimental** data compression/decompression routines, API subject to change!!

%import shared_compression

compression {
    %option no_symbol_prefixing, ignore_unused

     asmsub decode_rle_vram(uword compressed @R0, ubyte vbank @X, uword vaddr @AY) {
         ; -- Decodes "ByteRun1" (aka PackBits) RLE compressed data directly into Vera VRAM.
         ;    Control byte value 128 ends the decoding.  This routine is for the Commander X16 only.
         %asm {{
             stz  cx16.VERA_CTRL
             sta  cx16.VERA_ADDR_L
             sty  cx16.VERA_ADDR_M
             txa
             ora  #%00010000     ; autoincr by 1
             sta  cx16.VERA_ADDR_H
 _loop
             lda  (cx16.r0)
             bpl  _copy_literals
             cmp  #128
             bne  +
             rts  ; DONE!

             ; replicate the next byte -n+1 times
 +
             inc  cx16.r0L
             bne  +
             inc  cx16.r0H
 +           eor  #255
             clc
             adc  #2
             tay
             lda  (cx16.r0)
 -           sta  cx16.VERA_DATA0
             dey
             bne  -
             inc  cx16.r0L
             bne  _loop
             inc  cx16.r0H
             bra  _loop

 _copy_literals
             ; copy the next n+1 bytes
             inc  cx16.r0L
             bne  +
             inc  cx16.r0H
 +           pha
             tax
             inx
             ldy  #0
 -           lda  (cx16.r0),y
             sta  cx16.VERA_DATA0
             iny
             dex
             bne  -
             ; increase pointer by n+1 bytes
             pla
             sec
             adc  cx16.r0L
             sta  cx16.r0L
             bcc  _loop
             inc  cx16.r0H
             bra  _loop
         }}
     }

}
