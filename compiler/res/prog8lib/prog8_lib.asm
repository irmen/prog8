; Internal library routines - always included by the compiler
; Generic machine independent 6502 code.

		.section BSS
orig_stackpointer	.byte  ?	; stores the Stack pointer register at program start
		.send BSS

program_startup_clear_bss    .proc
	; this is always ran first thing from the start routine to clear out the BSS area
	.if  prog8_bss_section_size>0
		; reset all variables in BSS section to zero
		lda  #<prog8_bss_section_start
		ldy  #>prog8_bss_section_start
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldx  #<prog8_bss_section_size
		ldy  #>prog8_bss_section_size
		lda  #0
		jsr  prog8_lib.memset
	.endif
		rts
		.pend



read_byte_from_address_in_AY_into_A	.proc
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W2),y
		rts
		.pend

read_byte_from_address_in_AY_into_A_65c02	.proc
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  (P8ZP_SCRATCH_W2)
		rts
		.pend


write_byte_X_to_address_in_AY	.proc
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #0
		txa
		sta  (P8ZP_SCRATCH_W2),y
		rts
		.pend

write_byte_X_to_address_in_AY_65c02	.proc
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		txa
		sta  (P8ZP_SCRATCH_W2)
		rts
		.pend


reg_less_uw	.proc
		;  AY < P8ZP_SCRATCH_W2?
		cpy  P8ZP_SCRATCH_W2+1
		bcc  _true
		bne  _false
		cmp  P8ZP_SCRATCH_W2
		bcc  _true
_false		lda  #0
		rts
_true		lda  #1
		rts
		.pend

reg_less_w	.proc
		; -- AY < P8ZP_SCRATCH_W2?
		cmp  P8ZP_SCRATCH_W2
		tya
		sbc  P8ZP_SCRATCH_W2+1
		bvc  +
		eor  #$80
+		bmi  _true
		lda  #0
		rts
_true		lda  #1
		rts
		.pend

reg_lesseq_uw	.proc
		; AY <= P8ZP_SCRATCH_W2?
		cpy  P8ZP_SCRATCH_W2+1
		beq  +
		bcc  _true
		lda  #0
		rts
+		cmp  P8ZP_SCRATCH_W2
		bcc  _true
		beq  _true
		lda  #0
		rts
_true		lda  #1
		rts
		.pend

reg_lesseq_w	.proc
		; -- P8ZP_SCRATCH_W2 <= AY ?   (note: order different from other routines)
		cmp  P8ZP_SCRATCH_W2
		tya
		sbc  P8ZP_SCRATCH_W2+1
		bvc  +
		eor  #$80
+		bpl  +
		lda  #0
		rts
+		lda  #1
		rts
		.pend


memcopy16_up	.proc
	; -- copy memory UP from (P8ZP_SCRATCH_W1) to (P8ZP_SCRATCH_W2) of length X/Y (16-bit, X=lo, Y=hi)
	;    clobbers register A,X,Y
		source = P8ZP_SCRATCH_W1
		dest = P8ZP_SCRATCH_W2
		length = P8ZP_SCRATCH_B1   ; (and SCRATCH_ZPREG)

		stx  length
		sty  length+1

		ldx  length             ; move low byte of length into X
		bne  +                  ; jump to start if X > 0
		dec  length             ; subtract 1 from length
+		ldy  #0                 ; set Y to 0
-		lda  (source),y         ; set A to whatever (source) points to offset by Y
		sta  (dest),y           ; move A to location pointed to by (dest) offset by Y
		iny                     ; increment Y
		bne  +                  ; if Y<>0 then (rolled over) then still moving bytes
		inc  source+1           ; increment hi byte of source
		inc  dest+1             ; increment hi byte of dest
+		dex                     ; decrement X (lo byte counter)
		bne  -                  ; if X<>0 then move another byte
		dec  length             ; we've moved 255 bytes, dec length
		bpl  -                  ; if length is still positive go back and move more
		rts                     ; done
		.pend


memset          .proc
	; -- fill memory from (P8ZP_SCRATCH_W1), length XY, with value in A.
	;    clobbers X, Y
	; TODO: Romable
		stx  P8ZP_SCRATCH_B1
		sty  _save_reg
		ldy  #0
		ldx  _save_reg
		beq  _lastpage

_fullpage	sta  (P8ZP_SCRATCH_W1),y
		iny
		bne  _fullpage
		inc  P8ZP_SCRATCH_W1+1          ; next page
		dex
		bne  _fullpage

_lastpage	ldy  P8ZP_SCRATCH_B1
		beq  +
-         	dey
		sta  (P8ZP_SCRATCH_W1),y
		bne  -

+           	rts
		.section BSS
_save_reg	.byte  ?
		.send BSS
		.pend


memsetw		.proc
	; -- fill memory from (P8ZP_SCRATCH_W1) number of words in P8ZP_SCRATCH_W2, with word value in AY.
	;    clobbers A, X, Y
		sta  _val                    ; this used to be self-modify
		sty  _val+1
		ldx  P8ZP_SCRATCH_W1
		stx  P8ZP_SCRATCH_B1
		ldx  P8ZP_SCRATCH_W1+1
		inx
		stx  P8ZP_SCRATCH_REG                ; second page

		ldy  #0
		ldx  P8ZP_SCRATCH_W2+1
		beq  _lastpage

_fullpage
		lda  _val
		sta  (P8ZP_SCRATCH_W1),y        ; first page
		sta  (P8ZP_SCRATCH_B1),y            ; second page
		iny
		lda  _val+1
		sta  (P8ZP_SCRATCH_W1),y        ; first page
		sta  (P8ZP_SCRATCH_B1),y            ; second page
		iny
		bne  _fullpage
		inc  P8ZP_SCRATCH_W1+1          ; next page pair
		inc  P8ZP_SCRATCH_W1+1          ; next page pair
		inc  P8ZP_SCRATCH_B1+1              ; next page pair
		inc  P8ZP_SCRATCH_B1+1              ; next page pair
		dex
		bne  _fullpage

_lastpage	ldx  P8ZP_SCRATCH_W2
		beq  _done

		ldy  #0
-
		lda  _val
		sta  (P8ZP_SCRATCH_W1), y
		inc  P8ZP_SCRATCH_W1
		bne  _mod2b
		inc  P8ZP_SCRATCH_W1+1
		lda  _val+1
		sta  (P8ZP_SCRATCH_W1), y
		inc  P8ZP_SCRATCH_W1
		bne  +
		inc  P8ZP_SCRATCH_W1+1
+               dex
		bne  -
_done		rts
		.section BSS
_val	.word ?
		.send BSS
		.pend



ror2_mem_ub	.proc
		; -- in-place 8-bit ror of byte at memory location in AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		lsr  a
		bcc  +
		ora  #$80
+		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend

rol2_mem_ub	.proc
		; -- in-place 8-bit rol of byte at memory location in AY
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #$80
		rol  a
		sta  (P8ZP_SCRATCH_W1),y
		rts
		.pend


strcpy		.proc
		; copy a string (must be 0-terminated) from A/Y to (P8ZP_SCRATCH_W1)
		; it is assumed the target string is large enough.
		; returns the length of the string that was copied in Y.
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		ldy  #$ff
-		iny
		lda  (P8ZP_SCRATCH_W2),y
		sta  (P8ZP_SCRATCH_W1),y
		bne  -
		rts
		.pend

strcmp_expression	.proc
		; -- compare strings, result in A
		; TODO: Romable
		lda  _arg_s2
		ldy  _arg_s2+1
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  _arg_s1
		ldy  _arg_s1+1
		jmp  strcmp_mem
		.section BSS
_arg_s1		.word  ?
_arg_s2		.word  ?
		.send BSS
		.pend

strcmp_mem	.proc
		; --   compares strings in s1 (AY) and s2 (P8ZP_SCRATCH_W2).
		;      Returns -1,0,1 in A, depending on the ordering. Clobbers Y.
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
_loop           lda  (P8ZP_SCRATCH_W1),y
		beq  _c1_zero
		cmp  (P8ZP_SCRATCH_W2),y
		beq  _equal
		bmi  _less
		lda  #1
		rts
_less           lda  #-1
		rts
_equal          iny
		bne  _loop
_c1_zero        lda  (P8ZP_SCRATCH_W2),y
		beq  +
		lda  #-1
+		rts
		.pend

strncmp_mem	.proc
		; --   compares strings in s1 (AY) and s2 (P8ZP_SCRATCH_W2).
                ;      Compares up to maximum length specified in X.
		;      Returns -1,0,1 in A, depending on the ordering. Clobbers X & Y.
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
_loop           lda  (P8ZP_SCRATCH_W1),y
		beq  _c1_zero
		cmp  (P8ZP_SCRATCH_W2),y
		beq  _equal
		bmi  _less
		lda  #1
		rts
_less           lda  #-1
		rts
_equal          dex
		bne +
		lda #0
		rts
+		iny
		bne  _loop
_c1_zero        lda  (P8ZP_SCRATCH_W2),y
		beq  +
		lda  #-1
+		rts
		.pend


strlen          .proc
        ; -- returns the number of bytes in the string in AY, in Y. Clobbers A.
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		iny
		bne  -
+		rts
		.pend


containment_bytearray	.proc
	; -- check if a value exists in a byte array.
	;    parameters: P8ZP_SCRATCH_W1: address of the byte array, A = byte to check, Y = length of array (>=1).
	;    returns boolean 0/1 in A.
		dey
-		cmp  (P8ZP_SCRATCH_W1),y
		beq  +
		dey
		cpy  #255
		bne  -
		lda  #0
		rts
+		lda  #1
		rts
		.pend

containment_linearwordarray	.proc
	; -- check if a value exists in a linear word array.
	;    parameters: P8ZP_SCRATCH_W1: value to check, P8ZP_SCRATCH_W2: address of the word array, Y = number of elements in the array (>=1).
	;    returns boolean 0/1 in A.
		dey
		tya
		asl  a
		tay
-		lda  P8ZP_SCRATCH_W1
		cmp  (P8ZP_SCRATCH_W2),y
		bne  +
		lda  P8ZP_SCRATCH_W1+1
		iny
		cmp  (P8ZP_SCRATCH_W2),y
		beq  _found
		dey
+		dey
		dey
		cpy  #254
		bne  -
		lda  #0
		rts
_found		lda  #1
		rts
		.pend

containment_splitwordarray	.proc
	; -- check if a value exists in a split lsb/msb word array. (Assuming lsb array comes first, immediately followed by msb array)
	;    parameters: P8ZP_SCRATCH_W1: value to check, P8ZP_SCRATCH_W2: start address of the lsb word array, Y = number of elements in the array (>=1).
	;    returns boolean 0/1 in A.

	; store the needle value in SCRATCH_B1(lsb) and SCRATCH_REG(msb)
		lda  P8ZP_SCRATCH_W1
		sta  P8ZP_SCRATCH_B1
		lda  P8ZP_SCRATCH_W1+1
		sta  P8ZP_SCRATCH_REG

	; calculate where the msb array starts and put this in P8ZP_SCRATCH_W1  (_W2 is the start of the lsb array)
		tya
		clc
		adc  P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		lda  P8ZP_SCRATCH_W2+1
		adc  #0
		sta  P8ZP_SCRATCH_W1+1

	; search needle
		dey
-               lda  P8ZP_SCRATCH_REG
		cmp  (P8ZP_SCRATCH_W1),y
		bne  +
		lda  P8ZP_SCRATCH_B1
		cmp  (P8ZP_SCRATCH_W2),y
		beq  _found
+               dey
		cpy  #255
		bne  -
		lda  #0
		rts
_found          lda  #1
		rts
	.pend

; TODO: Romable
arraycopy_split_to_normal_words .proc
	; P8ZP_SCRATCH_W1 = start of lsb array
	; P8ZP_SCRATCH_W2 = start of msb array
	; AY = start of normal word target array
	; X = number of elements to copy
                sta  _modlsb+1
                sty  _modlsb+2
                clc
                adc  #1
                bne  +
                iny
+               sta  _modmsb+1
                sty  _modmsb+2
                ldy  #0
-               lda  (P8ZP_SCRATCH_W1),y
_modlsb         sta  $ffff       ; modified lsb store
                lda  _modlsb+1
                clc
                adc  #2
                sta  _modlsb+1
                bcc  +
                inc  _modlsb+2
+               lda  (P8ZP_SCRATCH_W2),y
_modmsb         sta  $ffff       ; modified msb store
                lda  _modmsb+1
                clc
                adc  #2
                sta  _modmsb+1
                bcc  +
                inc  _modmsb+2
+               iny
                dex
                bne  -
		rts
		.pend

; TODO: Romable
arraycopy_normal_to_split_words .proc
	; P8ZP_SCRATCH_W1 = start of target lsb array
	; P8ZP_SCRATCH_W2 = start of target msb array
	; AY = start of normal word source array
	; X = number of elements to copy
		sta  _modsrclsb+1
		sty  _modsrclsb+2
		clc
		adc  #1
		bne  +
		iny
+		sta  _modsrcmsb+1
		sty  _modsrcmsb+2
		ldy  #0
_modsrclsb      lda  $ffff      ; modified lsb read
		sta  (P8ZP_SCRATCH_W1),y
		lda  _modsrclsb+1
		clc
		adc  #2
		sta  _modsrclsb+1
		bcc  +
		inc  _modsrclsb+2
+
_modsrcmsb      lda  $ffff      ; modnfied msb read
		sta  (P8ZP_SCRATCH_W2),y
		lda  _modsrcmsb+1
		clc
		adc  #2
		sta  _modsrcmsb+1
		bcc  +
		inc  _modsrcmsb+2
+		iny
		dex
		bne  _modsrclsb
		rts
		.pend

memcopy_small   .proc
		; copy up to a single page (256 bytes) of memory.
		; note: only works for NON-OVERLAPPING memory regions!
		; P8ZP_SCRATCH_W1 = from address
		; P8ZP_SCRATCH_W2 = destination address
		; Y = number of bytes to copy  (where 0 means 256)
		cpy  #0
		beq  _fullpage
		dey
		beq  _lastbyte
_loop           lda  (P8ZP_SCRATCH_W1),y
                sta  (P8ZP_SCRATCH_W2),y
                dey
                bne  _loop
_lastbyte       lda  (P8ZP_SCRATCH_W1),y
                sta  (P8ZP_SCRATCH_W2),y
                rts
_fullpage       lda  (P8ZP_SCRATCH_W1),y
                sta  (P8ZP_SCRATCH_W2),y
                dey
                bne  _fullpage
                rts
		.pend
