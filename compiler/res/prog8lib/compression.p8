; **experimental** data compression/decompression routines, API subject to change!!

compression {

    %option no_symbol_prefixing, ignore_unused

    sub encode_rle_outfunc(uword data, uword size, uword output_function, bool is_last_block) {
        ; -- Compress the given data block using ByteRun1 aka PackBits RLE encoding.
        ;    output_function = address of a routine that gets a byte arg in A,
        ;                      which is the next RLE byte to write to the compressed output buffer or file.
        ;    is_last_block = usually true, but you can set it to false if you want to concatenate multiple
        ;                    compressed blocks (for instance if the source data is >64Kb)
        ;    Worst case result storage size needed = (size + (size+126) / 127) + 1
        ;    This routine is not optimized for speed but for readability and ease of use.
        uword idx = 0
        uword literals_start_idx = 0
        ubyte literals_length = 0

        asmsub call_output_function(ubyte arg @A) {
            %asm {{
                jmp  (p8v_output_function)
            }}
        }

        sub next_same_span() {
            ; returns length in cx16.r1L, and the byte value in cx16.r1H
            cx16.r1H = data[idx]
            cx16.r1L = 0
            while data[idx]==cx16.r1H and cx16.r1L<128 and idx<size {
                idx++
                cx16.r1L++
            }
        }

        sub output_literals() {
            call_output_function(literals_length-1)
            uword dataptr = data + literals_start_idx
            ubyte i
            for i in 0 to literals_length-1 {
                call_output_function(@(dataptr))
                dataptr++
            }
            literals_length = 0
        }

        while idx<size {
            next_same_span()     ; count in r1L, value in r1H
            if cx16.r1L>1 {
                ; a replicate run
                if literals_length>0
                    output_literals()
                call_output_function((cx16.r1L^255)+2)        ;  257-cx16.r1L
                call_output_function(cx16.r1H)
            }
            else {
                ; add more to the literals run
                if literals_length==128
                    output_literals()
                if literals_length==0
                    literals_start_idx = idx-1
                literals_length++
            }
        }

        if literals_length>0
            output_literals()

        if is_last_block
            call_output_function(128)
    }

    sub encode_rle(uword data, uword size, uword target, bool is_last_block) -> uword {
        ; -- Compress the given data block using ByteRun1 aka PackBits RLE encoding.
        ;    Returns the size of the compressed RLE data. Worst case result storage size needed = (size + (size+126) / 127) + 1.
        ;    is_last_block = usually true, but you can set it to false if you want to concatenate multiple
        ;                    compressed blocks (for instance if the source data is >64Kb)
        ;    This routine is not optimized for speed but for readability and ease of use.
        uword idx = 0
        uword literals_start_idx = 0
        ubyte literals_length = 0
        uword orig_target = target

        sub next_same_span() {
            ; returns length in cx16.r1L, and the byte value in cx16.r1H
            cx16.r1H = data[idx]
            cx16.r1L = 0
            while data[idx]==cx16.r1H and cx16.r1L<128 and idx<size {
                idx++
                cx16.r1L++
            }
        }

        sub output_literals() {
            @(target) = literals_length-1
            target++
            uword dataptr = data + literals_start_idx
            ubyte i
            for i in 0 to literals_length-1 {
                @(target) = @(dataptr)
                target++
                dataptr++
            }
            literals_length = 0
        }

        while idx<size {
            next_same_span()     ; count in r1L, value in r1H
            if cx16.r1L>1 {
                ; a replicate run
                if literals_length>0
                    output_literals()
                @(target) = (cx16.r1L^255)+2        ;  257-cx16.r1L
                target++
                @(target) = cx16.r1H
                target++
            }
            else {
                ; add more to the literals run
                if literals_length==128
                    output_literals()
                if literals_length==0
                    literals_start_idx = idx-1
                literals_length++
            }
        }

        if literals_length>0
            output_literals()

        if is_last_block {
            @(target) = 128
            target ++
        }

        return target-orig_target
    }

    asmsub decode_rle_srcfunc(uword source_function @AY, uword target @R0, uword maxsize @R1) clobbers(X) -> uword @AY {
        ; -- Decodes "ByteRun1" (aka PackBits) RLE compressed data. Control byte value 128 ends the decoding.
        ;    Also stops decompressing when the maxsize has been reached. Returns the size of the decompressed data.
        ;    Instead of a source buffer, you provide a callback function that must return the next byte to compress in A.
        %asm {{
            sta  _cb_mod1+1
            sty  _cb_mod1+2
            sta  _cb_mod2+1
            sty  _cb_mod2+2
            sta  _cb_mod3+1
            sty  _cb_mod3+2
            lda  cx16.r0L
            ldy  cx16.r0H
            sta  P8ZP_SCRATCH_W2        ; target ptr
            sta  _orig_target
            sty  P8ZP_SCRATCH_W2+1
            sty  _orig_target+1
            lda  cx16.r0L
            clc
            adc  cx16.r1L
            sta  cx16.r1L
            lda  cx16.r0H
            adc  cx16.r1H
            sta  cx16.r1H        ; decompression limit

_loop
	        ; while target (W2) < limit (r1)
        	lda  P8ZP_SCRATCH_W2
	        ldy  P8ZP_SCRATCH_W2+1
	        cmp  cx16.r1L
	        tya
	        sbc  cx16.r1H
	        bcs  _end

_cb_mod1    jsr  $ffff      ; modified
            bpl  _literals
            cmp  #128
            beq  _end

            ; replicate next byte -n+1 times
            eor  #255
            clc
            adc  #2
            sta  P8ZP_SCRATCH_REG
_cb_mod2    jsr  $ffff      ; modified
            ldx  P8ZP_SCRATCH_REG
            ldy  #0
-           sta  (P8ZP_SCRATCH_W2),y
            iny
            dex
            bne  -
            ; add A to target
            lda  P8ZP_SCRATCH_REG
            clc
            adc  P8ZP_SCRATCH_W2
            sta  P8ZP_SCRATCH_W2
            lda  #0
            adc  P8ZP_SCRATCH_W2+1
            sta  P8ZP_SCRATCH_W2+1
            jmp  _loop
_literals
            ; copy the next n+1 bytes
            pha
            sta  P8ZP_SCRATCH_B1
            ldy  #0
            sty  P8ZP_SCRATCH_REG
_cb_mod3    jsr  $ffff      ; modified
            ldy  P8ZP_SCRATCH_REG
            sta  (P8ZP_SCRATCH_W2),y
            inc  P8ZP_SCRATCH_REG
            dec  P8ZP_SCRATCH_B1
            bpl  _cb_mod3
            ; add N+1 to target
            pla
            sec
            adc  P8ZP_SCRATCH_W2
            sta  P8ZP_SCRATCH_W2
            lda  #0
            adc  P8ZP_SCRATCH_W2+1
            sta  P8ZP_SCRATCH_W2+1
            jmp  _loop

_orig_target    .word  0
_end
            ; return w2-orig_target, the size of the decompressed data
            lda  P8ZP_SCRATCH_W2
            ldy  P8ZP_SCRATCH_W2+1
            sec
            sbc  _orig_target
            tax
            tya
            sbc  _orig_target+1
            tay
            txa
            rts
        }}
    }

    asmsub decode_rle(uword compressed @AY, uword target @R0, uword maxsize @R1) clobbers(X) -> uword @AY {
        ; -- Decodes "ByteRun1" (aka PackBits) RLE compressed data. Control byte value 128 ends the decoding.
        ;    Also stops decompressing if the maxsize has been reached.
        ;    Returns the size of the decompressed data.
        %asm {{
            sta  P8ZP_SCRATCH_W1        ; compressed data ptr
            sty  P8ZP_SCRATCH_W1+1
            lda  cx16.r0L
            ldy  cx16.r0H
            sta  P8ZP_SCRATCH_W2        ; target ptr
            sta  _orig_target
            sty  P8ZP_SCRATCH_W2+1
            sty  _orig_target+1
            lda  cx16.r0L
            clc
            adc  cx16.r1L
            sta  cx16.r1L
            lda  cx16.r0H
            adc  cx16.r1H
            sta  cx16.r1H        ; decompression limit

_loop       ; while target (W2) < limit (r1)
        	lda  P8ZP_SCRATCH_W2
	        ldy  P8ZP_SCRATCH_W2+1
	        cmp  cx16.r1L
	        tya
	        sbc  cx16.r1H
	        bcs  _end

            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y
            bpl  _literals
            cmp  #128
            beq  _end

            ; replicate next byte -n+1 times
            eor  #255
            clc
            adc  #2
            pha
            tax
            iny
            lda  (P8ZP_SCRATCH_W1),y
            ldy  #0
-           sta  (P8ZP_SCRATCH_W2),y
            iny
            dex
            bne  -
            ; add A to target
            pla
            clc
            adc  P8ZP_SCRATCH_W2
            sta  P8ZP_SCRATCH_W2
            lda  #0
            adc  P8ZP_SCRATCH_W2+1
            sta  P8ZP_SCRATCH_W2+1
            ; increase source by 2
            clc
            lda  P8ZP_SCRATCH_W1
            adc  #2
            sta  P8ZP_SCRATCH_W1
            lda  #0
            adc  P8ZP_SCRATCH_W1+1
            sta  P8ZP_SCRATCH_W1+1
            jmp  _loop
_literals
            ; copy the next n+1 bytes
            pha
            tax
            inc  P8ZP_SCRATCH_W1
            bne  +
            inc  P8ZP_SCRATCH_W1+1
+           ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  (P8ZP_SCRATCH_W2),y
            iny
            dex
            bpl  -
            ; add N+1 to source
            pla
            tax
            sec
            adc  P8ZP_SCRATCH_W1
            sta  P8ZP_SCRATCH_W1
            lda  #0
            adc  P8ZP_SCRATCH_W1+1
            sta  P8ZP_SCRATCH_W1+1
            ; add N+1 to target as well
            txa
            sec
            adc  P8ZP_SCRATCH_W2
            sta  P8ZP_SCRATCH_W2
            lda  #0
            adc  P8ZP_SCRATCH_W2+1
            sta  P8ZP_SCRATCH_W2+1
            jmp  _loop

_orig_target    .word  0
_end
            ; return w2-orig_target, the size of the decompressed data
            lda  P8ZP_SCRATCH_W2
            ldy  P8ZP_SCRATCH_W2+1
            sec
            sbc  _orig_target
            tax
            tya
            sbc  _orig_target+1
            tay
            txa
            rts
        }}
    }

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

/***
    ; prog8 source code for the asm routine above:

    sub decode_rle_prog8(uword @zp compressed, uword @zp target, uword maxsize) -> uword {
        cx16.r0 = target    ; original target
        cx16.r1 = target+maxsize     ; decompression limit

        while target<cx16.r1 {
            cx16.r2L = @(compressed)
            if_neg {
                if cx16.r2L==128
                    break
                ; replicate the next byte -n+1 times
                compressed++
                cx16.r3L = @(compressed)
                repeat 2+(cx16.r2L^255) {
                    @(target) = cx16.r3L
                    target++
                }
                compressed++
            } else {
                ; copy the next n+1 bytes
                compressed++
                repeat cx16.r2L+1 {
                    @(target) = @(compressed)
                    compressed++
                    target++
                }
            }
        }
        return target-cx16.r0
    }

    sub decode_rle_callback_prog8(uword producer_callback, uword @zp target, uword maxsize) -> uword {
        cx16.r0 = target   ; original target
        cx16.r1 = target+maxsize     ; decompression limit

        while target<cx16.r1 {
            cx16.r2L = lsb(call(producer_callback))
            if_neg {
                if cx16.r2L==128
                    break
                ; replicate the next byte -n+1 times
                cx16.r3L = lsb(call(producer_callback))
                repeat 2+(cx16.r2L^255) {
                    @(target) = cx16.r3L
                    target++
                }
            } else {
                ; copy the next n+1 bytes
                repeat cx16.r2L+1 {
                    @(target) = lsb(call(producer_callback))
                    target++
                }
            }
        }
        return target-cx16.r0
    }
***/

}
