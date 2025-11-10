; data compression/decompression routines
; This file contains the shared routines that work on all targets.

compression {

    %option ignore_unused, merge

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
        ;    Instead of a source buffer, you provide a callback function that must return the next byte to decompress in A.
        ;    Note: the callback routine MUST NOT MODIFY the prog8 scratch variables such as P8ZP_SCRATCH_W1 etc!
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

            ; check control byte
_cb_mod1    jsr  $ffff      ; modified
            cmp  #0
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
            bcc  _loop
            inc  P8ZP_SCRATCH_W2+1
            bcs  _loop

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
            bcc  _loop
            inc  P8ZP_SCRATCH_W2+1
            bcs  _loop

            .section BSS
_orig_target    .word  ?
            .send BSS

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

            ; check control byte
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
            bcc  +
            inc  P8ZP_SCRATCH_W2+1
            clc
+
            ; increase source by 2
            lda  P8ZP_SCRATCH_W1
            adc  #2
            sta  P8ZP_SCRATCH_W1
            bcc  _loop
            inc  P8ZP_SCRATCH_W1+1
            bcs  _loop

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
            bcc  +
            inc  P8ZP_SCRATCH_W1+1
+           ; add N+1 to target as well
            txa
            sec
            adc  P8ZP_SCRATCH_W2
            sta  P8ZP_SCRATCH_W2
            bcc  _loop
            inc  P8ZP_SCRATCH_W2+1
            bcs  _loop

            .section BSS
_orig_target    .word  ?
            .send BSS

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


	asmsub decode_zx0(uword compressed @R0, uword target @R1) clobbers(A,X,Y) {
        ; Decompress a block of data compressed in the ZX0 format
        ; This can be produced using the "salvador" compressor with -classic
        ; It has faster decompression than LZSA and a better compression ratio as well.
        ; see https://github.com/emmanuel-marty/salvador for the compressor tool
        ; see https://github.com/einar-saukas/ZX0  for the compression format
        ;
        ; NOTE: for speed reasons this decompressor is NOT bank-aware and NOT I/O register aware;
        ;       it only outputs to a memory buffer somewhere in the active 64 Kb address range
        ;

        %asm {{

; ***************************************************************************
; ***************************************************************************
;
; zx0_6502.asm
;
; NMOS 6502 decompressor for data stored in Einar Saukas's ZX0 format.
;
; The code is 196 bytes long, and is self-modifying.
;
; Copyright John Brandwood 2021.
;
; Distributed under the Boost Software License, Version 1.0.
; (See accompanying file LICENSE_1_0.txt or copy at
;  http://www.boost.org/LICENSE_1_0.txt)
;
; Adapted for Prog8 by Irmen de Jong
;
; ***************************************************************************
; ***************************************************************************



; ***************************************************************************
; ***************************************************************************
;
; Decompression Options & Macros
;



; ***************************************************************************
; ***************************************************************************
;
; Data usage is 8 bytes of zero-page.
;

.if cx16.r0 < $100
    ; r0-r15 registers are in zeropage just use those
zx0_srcptr      =       cx16.r0   ;$F8                     ; 1 word.
zx0_dstptr      =       cx16.r1   ;$FA                     ; 1 word.
zx0_length      =       cx16.r2   ;$FC                     ; 1 word.
zx0_offset      =       cx16.r3   ;$FE                     ; 1 word.
.else
    .error "in decode_zx0: r0-15 are not in zeropage and no alternatives have been set up yet"     ; TODO
.endif


; ***************************************************************************
; ***************************************************************************
;
; zx0_unpack - Decompress data stored in Einar Saukas's ZX0 format.
;
; Args: zx0_srcptr = ptr to compessed data
; Args: zx0_dstptr = ptr to output buffer
; Uses: lots!
;

zx0_unpack:

.if cx16.r0>=$100
                ; set up the source and destination pointers
                lda  cx16.r0L
                sta  zx0_srcptr
                lda  cx16.r0H
                sta  zx0_srcptr+1
                lda  cx16.r1L
                sta  zx0_dstptr
                lda  cx16.r1H
                sta  zx0_dstptr+1
.endif


                ldy     #$FF                    ; Initialize default offset.
                sty     <zx0_offset+0
                sty     <zx0_offset+1
                iny                             ; Initialize source index.
                sty     <zx0_length+1           ; Initialize length to 1.

                ldx     #$40                    ; Initialize empty buffer.

zx0_next_cmd:   lda     #1                      ; Initialize length back to 1.
                sta     <zx0_length + 0

                txa                             ; Restore bit-buffer.

                asl     a                        ; Copy from literals or new offset?
                bcc     zx0_cp_literal

                ;
                ; Copy bytes from new offset.
                ;

zx0_new_offset: jsr     zx0_gamma_flag          ; Get offset MSB, returns CS.

                tya                             ; Negate offset MSB and check
                sbc     <zx0_length + 0         ; for zero (EOF marker).
                bcs     zx0_got_eof

                sec
                ror     a
                sta     <zx0_offset + 1         ; Save offset MSB.

                lda     (<zx0_srcptr),y         ; Get offset LSB.
                inc     <zx0_srcptr + 0
                beq     zx0_inc_of_src

zx0_off_skip1:  ror     a                        ; Last offset bit starts gamma.
                sta     <zx0_offset + 0         ; Save offset LSB.

                lda     #-2                     ; Minimum length of 2?
                bcs     zx0_get_lz_dst

                lda     #1                      ; Initialize length back to 1.
                sta     <zx0_length + 0

                txa                             ; Restore bit-buffer.

                jsr     zx0_gamma_data          ; Get length, returns CS.

                lda     <zx0_length + 0         ; Negate lo-byte of (length+1).
                eor     #$FF

;               bne     zx0_get_lz_dst          ; N.B. Optimized to do nothing!
;
;               inc     <zx0_length + 1         ; Increment from (length+1).
;               dec     <zx0_length + 1         ; Decrement because lo-byte=0.

zx0_get_lz_dst: tay                             ; Calc address of partial page.
                eor     #$FF                    ; Always CS from previous SBC.
                adc     <zx0_dstptr + 0
                sta     <zx0_dstptr + 0
                bcs     zx0_get_lz_win

                dec     <zx0_dstptr + 1

zx0_get_lz_win: clc                             ; Calc address of match.
                adc     <zx0_offset + 0         ; N.B. Offset is negative!
                sta     zx0_winptr + 0
                lda     <zx0_dstptr + 1
                adc     <zx0_offset + 1
                sta     zx0_winptr + 1

zx0_winptr      =       *+1

zx0_lz_page:    lda     $1234,y                 ; Self-modifying zx0_winptr.
                sta     (<zx0_dstptr),y
                iny
                bne     zx0_lz_page
                inc     <zx0_dstptr + 1

                lda     <zx0_length + 1         ; Any full pages left to copy?
                beq     zx0_next_cmd

                dec     <zx0_length + 1         ; This is rare, so slower.
                inc     zx0_winptr + 1
                bne     zx0_lz_page             ; Always true.

zx0_got_eof:    rts                             ; Finished decompression.

                ;
                ; Copy bytes from compressed source.
                ;

zx0_cp_literal: jsr     zx0_gamma_flag          ; Get length, returns CS.

                pha                             ; Preserve bit-buffer.

                ldx     <zx0_length + 0         ; Check the lo-byte of length
                bne     zx0_cp_byte             ; without effecting CS.

zx0_cp_page:    dec     <zx0_length + 1         ; Decrement # of pages to copy.

zx0_cp_byte:    lda     (<zx0_srcptr),y         ; CS throughout the execution of
                sta     (<zx0_dstptr),y         ; of this .cp_page loop.

                inc     <zx0_srcptr + 0
                beq     zx0_inc_cp_src

zx0_cp_skip1:   inc     <zx0_dstptr + 0
                beq     zx0_inc_cp_dst

zx0_cp_skip2:   dex                             ; Any bytes left to copy?
                bne     zx0_cp_byte

                lda     <zx0_length + 1         ; Any full pages left to copy?
                bne     zx0_cp_page             ; Optimized for branch-unlikely.

                inx                             ; Initialize length back to 1.
                stx     <zx0_length + 0

                pla                             ; Restore bit-buffer.

                asl     a                        ; Copy from last offset or new offset?
                bcs     zx0_new_offset

                ;
                ; Copy bytes from last offset (rare so slower).
                ;

zx0_old_offset: jsr     zx0_gamma_flag          ; Get length, returns CS.

                tya                             ; Negate the lo-byte of length.
                sbc     <zx0_length + 0
                sec                             ; Ensure CS before zx0_get_lz_dst!
                bne     zx0_get_lz_dst

                dec     <zx0_length + 1         ; Decrement because lo-byte=0.
                bcs     zx0_get_lz_dst          ; Always true!

                ;
                ; Optimized handling of pointers crossing page-boundaries.
                ;

zx0_inc_of_src: inc     <zx0_srcptr + 1
                bne     zx0_off_skip1           ; Always true.

zx0_inc_cp_src: inc     <zx0_srcptr + 1
                bcs     zx0_cp_skip1            ; Always true.

zx0_inc_cp_dst: inc     <zx0_dstptr + 1
                bcs     zx0_cp_skip2            ; Always true.

zx0_inc_ga_src: inc     <zx0_srcptr + 1
                bne     zx0_gamma_skip          ; Always true.

                ;
                ; Get 16-bit interlaced Elias gamma value.
                ;

zx0_gamma_data: asl     a                        ; Get next bit.
                rol     <zx0_length + 0
zx0_gamma_flag: asl     a
                bcc     zx0_gamma_data          ; Loop until finished or empty.
                bne     zx0_gamma_done          ; Bit-buffer empty?

zx0_gamma_load: lda     (<zx0_srcptr),y         ; Reload the empty bit-buffer
                inc     <zx0_srcptr + 0         ; from the compressed source.
                beq     zx0_inc_ga_src
zx0_gamma_skip: rol     a
                bcs     zx0_gamma_done          ; Finished?

zx0_gamma_word: asl     a                       ; Get next bit.
                rol     <zx0_length + 0
                rol     <zx0_length + 1
                asl     a
                bcc     zx0_gamma_word          ; Loop until finished or empty.
                beq     zx0_gamma_load          ; Bit-buffer empty?

zx0_gamma_done: tax                             ; Preserve bit-buffer.
                rts

        }}

    }


    asmsub decode_tscrunch(uword compressed @R0, uword target @R1) clobbers(A,X,Y) {
        ; Decompress a block of data compressed by TSCRUNCH
        ; see https://github.com/tonysavon/TSCrunch
        ; It has extremely fast decompression (approaching RLE speeds),
        ; better compression as RLE, but slightly worse compression ration than LZSA
        ;
        ; NOTE: for speed reasons this decompressor is NOT bank-aware and NOT I/O register aware;
        ;       it only outputs to a memory buffer somewhere in the active 64 Kb address range

        %asm {{

;NMOS 6502 decompressor for data stored in TSCrunch format.
;
;Copyright Antonio Savona 2022.
; Distributed under the Apache software License v2.0 https://www.apache.org/licenses/LICENSE-2.0
;
; Adapted for Prog8 and 6502 CMOS by Irmen de Jong.


.if cx16.r0 < $100
    ; r0-r15 registers are in zeropage just use those
tsget 	= cx16.r0	; 2 bytes
tsput 	= cx16.r1	; 2 bytes
tstemp	= cx16.r2
lzput 	= cx16.r3	; 2 bytes
.else
    .error "in decode_tscrunch: r0-15 are not in zeropage and no alternatives have been set up yet"     ; TODO
.endif


.if cx16.r0>=$100
            ; set up the source and destination pointers
            lda  cx16.r0L
            sta  tsget
            lda  cx16.r0H
            sta  tsget+1
            lda  cx16.r1L
            sta  tsput
            lda  cx16.r1H
            sta  tsput+1
.endif

			ldy #0
			lda (tsget),y
			sta optRun + 1
			inc tsget
			bne entry2
			inc tsget + 1

	entry2:
			; ILLEGAL lax (tsget),y
			lda (tsget),y
			tax

			bmi rleorlz

			cmp #$20
			bcs lz2

	; literal

			tay

		ts_delit_loop:

			lda (tsget),y
			dey
			sta (tsput),y

			bne ts_delit_loop

			txa
			inx

	updatezp_noclc:
			adc tsput
			sta tsput
			bcs updateput_hi
		putnoof:
			txa
		update_getonly:
			adc tsget
			sta tsget
			bcc entry2
			inc tsget+1
			bcs entry2

	updateput_hi:
			inc tsput+1
			clc
			bcc putnoof

	rleorlz:

			; ILLEGAL: alr #$7f
			and #$7f
			lsr a
			bcc ts_delz

		; RLE
			beq optRun

		plain:
			ldx #2
			iny
			sta tstemp		; number of bytes to de-rle

			lda (tsget),y	; fetch rle byte
			ldy tstemp
		runStart:
			sta (tsput),y

		ts_derle_loop:

			dey
			sta (tsput),y

			bne ts_derle_loop

			; update zero page with a = runlen, x = 2 , y = 0
			lda tstemp

			bcs updatezp_noclc

	   done:
			rts
	; LZ2
		lz2:
			beq done

			ora #$80
			adc tsput
			sta lzput
			lda tsput + 1
			sbc #$00
			sta lzput + 1

			; y already zero
			lda (lzput),y
			sta (tsput),y
			iny
			lda (lzput),y
			sta (tsput),y

			tya
			dey

			adc tsput
			sta tsput
			bcs lz2_put_hi
		skp:
			inc tsget
			bne entry2
			inc tsget + 1
			bne entry2

		lz2_put_hi:
			inc tsput + 1
			bcs skp

	; LZ
	ts_delz:

			lsr a
			sta lzto + 1

			iny

			lda tsput
			bcc long

			sbc (tsget),y
			sta lzput
			lda tsput+1

			sbc #$00

			ldx #2
			; lz MUST decrunch forward
	lz_put:
			sta lzput+1

			ldy #0

			lda (lzput),y
			sta (tsput),y

			iny
			lda (lzput),y
			sta (tsput),y

	ts_delz_loop:

			iny

			lda (lzput),y
			sta (tsput),y

	lzto:	cpy #0
			bne ts_delz_loop

			tya

			; update zero page with a = runlen, x = 2, y = 0
			ldy #0
			; clc not needed as we have len - 1 in A (from the encoder) and C = 1

			jmp updatezp_noclc

	optRun:
			ldy #255
			sty tstemp

			ldx #1
			; A is zero

			bne runStart

	long:
			; carry is clear and compensated for from the encoder
			adc (tsget),y
			sta lzput
			iny
			; ILLEGAL: lax (tsget),y
			lda  (tsget),y
			tax
			ora #$80
			adc tsput + 1

			cpx #$80
			rol lzto + 1
			ldx #3

			bne lz_put

            ; !notreached!
        }}

    }


    asmsub decode_tscrunch_inplace(uword compressed @R0) clobbers(A,X,Y) {
        ; Decompress a block of data compressed by TSCRUNCH *in place*
        ; This can save an extra memory buffer if you are reading crunched data from a file into a buffer.
        ; see https://github.com/tonysavon/TSCrunch
        ; It has extremely fast decompression (approaching RLE speeds),
        ; better compression as RLE, but slightly worse compression ration than LZSA
        ;
        ; NOTE: to allow in-place decompression you need to use -i switch when crunching.
        ;       also, both the input data file and compressed data file are PRG files with a load header!
        ; NOTE: for speed reasons this decompressor is NOT bank-aware and NOT I/O register aware;
        ;       it only outputs to a memory buffer somewhere in the active 64 Kb address range
        %asm {{

; NMOS 6502 decompressor for data stored in TSCrunch format.
;
; Copyright Antonio Savona 2022.
; Distributed under the Apache software License v2.0 https://www.apache.org/licenses/LICENSE-2.0
;
; Adapted for Prog8 and 6502 CMOS by Irmen de Jong.



.if cx16.r0 < $100
    ; r0-r15 registers are in zeropage just use those
tsget 	= cx16.r0	; 2 bytes
tsput 	= cx16.r1	; 2 bytes
tstemp	= cx16.r2
lzput 	= cx16.r3	; 2 bytes
.else
    .error "in decode_tscrunch: r0-15 are not in zeropage and no alternatives have been set up yet"     ; TODO
.endif


.if cx16.r0>=$100
            ; set up the source and destination pointer
            lda  cx16.r0L
            sta  tsget
            lda  cx16.r0H
            sta  tsget+1
.endif


			ldy #$ff
		-	iny
			lda (tsget),y
			sta tsput , y	; last iteration trashes lzput, with no effect.
			cpy #3
			bne -

			pha

			lda lzput
			sta optRun + 1

			tya
			ldy #0
			beq update_getonly

	entry2:
			; ILLEGAL lax (tsget),y
			lda (tsget),y
			tax

			bmi rleorlz

			cmp #$20
			bcs lz2

	; literal

			inc tsget
			beq updatelit_hi
		return_from_updatelit:

		ts_delit_loop:

			lda (tsget),y
			sta (tsput),y
			iny
			dex

			bne ts_delit_loop

			tya
			tax
			; carry is clear
			ldy #0

	updatezp_noclc:
			adc tsput
			sta tsput
			bcs updateput_hi
		putnoof:
			txa
		update_getonly:
			adc tsget
			sta tsget
			bcc entry2
			inc tsget+1
			bcs entry2

	updatelit_hi:
			inc tsget+1
			bcc return_from_updatelit
	updateput_hi:
			inc tsput+1
			clc
			bcc putnoof

	rleorlz:

			; ILLEGAL: alr #$7f
			and #$7f
			lsr a
			bcc ts_delz

		; RLE
			beq optRun

		plain:
			ldx #2
			iny
			sta tstemp		; number of bytes to de-rle

			lda (tsget),y	; fetch rle byte
			ldy tstemp
		runStart:
			sta (tsput),y

		ts_derle_loop:

			dey
			sta (tsput),y

			bne ts_derle_loop

			; update zero page with a = runlen, x = 2 , y = 0
			lda tstemp

			bcs updatezp_noclc

	   done:
	   		pla
	   		sta (tsput),y
			rts
	; LZ2
		lz2:
			beq done

			ora #$80
			adc tsput
			sta lzput
			lda tsput + 1
			sbc #$00
			sta lzput + 1

			; y already zero
			lda (lzput),y
			sta (tsput),y
			iny
			lda (lzput),y
			sta (tsput),y

			tya
			dey

			adc tsput
			sta tsput
			bcs lz2_put_hi
		skp:
			inc tsget
			bne entry2
			inc tsget + 1
			bne entry2

		lz2_put_hi:
			inc tsput + 1
			bcs skp

	; LZ
	ts_delz:

			lsr a
			sta lzto + 1

			iny

			lda tsput
			bcc long

			sbc (tsget),y
			sta lzput
			lda tsput+1

			sbc #$00

			ldx #2
			; lz MUST decrunch forward
	lz_put:
			sta lzput+1

			ldy #0

			lda (lzput),y
			sta (tsput),y

			iny
			lda (lzput),y
			sta (tsput),y

	ts_delz_loop:

			iny

			lda (lzput),y
			sta (tsput),y

	lzto:	cpy #0
			bne ts_delz_loop

			tya

			; update zero page with a = runlen, x = 2, y = 0
			ldy #0
			; clc not needed as we have len - 1 in A (from the encoder) and C = 1

			jmp updatezp_noclc

	optRun:
			ldy #255
			sty tstemp

			ldx #1
			; A is zero

			bne runStart

	long:
			; carry is clear and compensated for from the encoder
			adc (tsget),y
			sta lzput
			iny
			; ILLEGAL lax (tsget),y
			lda (tsget),y
			tax
			ora #$80
			adc tsput + 1

			cpx #$80
			rol lzto + 1
			ldx #3

			bne lz_put

	        ; !notreached!
        }}
    }


/***
    ; prog8 source code for the RLE routines above:

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
