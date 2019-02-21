; Prog8 definitions for the Commodore-64
; These are the utility subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


%import c64lib


~ c64utils {

		const   uword  ESTACK_LO	= $ce00
		const   uword  ESTACK_HI	= $cf00


; ----- utility functions ----

asmsub  ubyte2decimal  (ubyte value @ A) -> clobbers() -> (ubyte @ Y, ubyte @ X, ubyte @ A)  {
	; ---- A to decimal string in Y/X/A  (100s in Y, 10s in X, 1s in A)
	%asm {{
		ldy  #$2f
		ldx  #$3a
		sec
-               iny
		sbc  #100
		bcs  -
-               dex
		adc  #10
		bmi  -
		adc  #$2f
		rts
	}}
}

asmsub  byte2decimal  (ubyte value @ A) -> clobbers() -> (ubyte @ Y, ubyte @ X, ubyte @ A)  {
	; ---- A (signed byte) to decimal string in Y/X/A  (100s in Y, 10s in X, 1s in A)
	;      note: the '-' is not part of the conversion here if it's a negative number
	%asm {{
		cmp  #0
		bpl  +
		eor  #255
		clc
		adc  #1
+		jmp  ubyte2decimal
	}}
}

asmsub  ubyte2hex  (ubyte value @ A) -> clobbers() -> (ubyte @ A, ubyte @ Y)  {
	; ---- A to hex string in AY (first hex char in A, second hex char in Y)
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		pha
		and  #$0f
		tax
		ldy  hex_digits,x
		pla
		lsr  a
		lsr  a
		lsr  a
		lsr  a
		tax
		lda  hex_digits,x
		ldx  c64.SCRATCH_ZPREGX
		rts

hex_digits	.text "0123456789abcdef"	; can probably be reused for other stuff as well
	}}
}


		str  word2hex_output = "1234"   ; 0-terminated, to make printing easier
asmsub  uword2hex  (uword value @ AY) -> clobbers(A,Y) -> ()  {
	; ---- convert 16 bit uword in A/Y into 4-character hexadecimal string into memory  'word2hex_output'
	%asm {{
		sta  c64.SCRATCH_ZPREG
		tya
		jsr  ubyte2hex
		sta  word2hex_output
		sty  word2hex_output+1
		lda  c64.SCRATCH_ZPREG
		jsr  ubyte2hex
		sta  word2hex_output+2
		sty  word2hex_output+3
		rts
	}}
}

		ubyte[3]  word2bcd_bcdbuff = [0, 0, 0]
asmsub  uword2bcd  (uword value @ AY) -> clobbers(A,Y) -> ()  {
	; Convert an 16 bit binary value to BCD
	;
	; This function converts a 16 bit binary value in A/Y into a 24 bit BCD. It
	; works by transferring one bit a time from the source and adding it
	; into a BCD value that is being doubled on each iteration. As all the
	; arithmetic is being done in BCD the result is a binary to decimal
	; conversion.
	%asm {{
		sta  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		sei				; disable interrupts because of bcd math
		sed				; switch to decimal mode
		lda  #0				; ensure the result is clear
		sta  word2bcd_bcdbuff+0
		sta  word2bcd_bcdbuff+1
		sta  word2bcd_bcdbuff+2
		ldy  #16			; the number of source bits

-		asl  c64.SCRATCH_ZPB1		; shift out one bit
		rol  c64.SCRATCH_ZPREG
		lda  word2bcd_bcdbuff+0		; and add into result
		adc  word2bcd_bcdbuff+0
		sta  word2bcd_bcdbuff+0
		lda  word2bcd_bcdbuff+1		; propagating any carry
		adc  word2bcd_bcdbuff+1
		sta  word2bcd_bcdbuff+1
		lda  word2bcd_bcdbuff+2		; ... thru whole result
		adc  word2bcd_bcdbuff+2
		sta  word2bcd_bcdbuff+2
		dey				; and repeat for next bit
		bne  -
		cld				; back to binary
		cli				; enable interrupts again      @todo don't re-enable if it wasn't enabled before
		rts
	}}
}


		ubyte[5]  word2decimal_output = 0
asmsub  uword2decimal  (uword value @ AY) -> clobbers(A,Y) -> ()  {
	; ---- convert 16 bit uword in A/Y into decimal string into memory  'word2decimal_output'
	%asm {{
		jsr  uword2bcd
		lda  word2bcd_bcdbuff+2
		clc
		adc  #'0'
		sta  word2decimal_output
		ldy  #1
		lda  word2bcd_bcdbuff+1
		jsr  +
		lda  word2bcd_bcdbuff+0

+		pha
		lsr  a
		lsr  a
		lsr  a
		lsr  a
		clc
		adc  #'0'
		sta  word2decimal_output,y
		iny
		pla
		and  #$0f
		adc  #'0'
		sta  word2decimal_output,y
		iny
		rts
	}}

}


asmsub  str2byte  (str string @ AY) -> clobbers(Y) -> (byte @ A) {
	%asm {{
		; -- convert string (address in A/Y) to byte in A
		;    doesn't use any kernal routines
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #0
		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  #'-'
		beq  +
		jmp  str2ubyte._enter
+		inc  c64.SCRATCH_ZPWORD1
		bne  +
		inc  c64.SCRATCH_ZPWORD1+1
+		jsr  str2ubyte._enter
		eor  #$ff
		sec
		adc  #0
		rts
	}}
}

asmsub  str2ubyte  (str string @ AY) -> clobbers(Y) -> (ubyte @ A) {
	%asm {{
		; -- convert string (address in A/Y) to ubyte in A
		;    doesn't use any kernal routines
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
_enter		jsr  _numlen			; Y= slen
		lda  #0
		dey
		bpl  +
		rts
+		lda  (c64.SCRATCH_ZPWORD1),y
		sec
		sbc  #'0'
		dey
		bpl  +
		rts
+		sta  c64.SCRATCH_ZPREG		;result
		lda  (c64.SCRATCH_ZPWORD1),y
		sec
		sbc  #'0'
		asl  a
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1
		clc
		adc  c64.SCRATCH_ZPREG
		dey
		bpl  +
		rts
+		sta  c64.SCRATCH_ZPREG
		lda  (c64.SCRATCH_ZPWORD1),y
		tay
		lda  _hundreds-'0',y
		clc
		adc  c64.SCRATCH_ZPREG
		rts
_hundreds	.byte  0, 100, 200

_numlen
		;-- return the length of the numeric string at ZPWORD1, in Y
		ldy  #0
-		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  #'0'
		bmi  +
		cmp  #':'	; one after '9'
		bpl  +
		iny
		bne  -
+		rts

	}}
}


asmsub	c64flt_FREADSTR	(ubyte length @ A) -> clobbers(A,X,Y) -> ()	= $b7b5		; @todo needed for (slow) str conversion below
asmsub	c64flt_GETADR	() -> clobbers(X) -> (ubyte @ Y, ubyte @ A)	= $b7f7		; @todo needed for (slow) str conversion below
asmsub	c64flt_FTOSWORDYA  () -> clobbers(X) -> (ubyte @ Y, ubyte @ A)	= $b1aa		; @todo needed for (slow) str conversion below

asmsub  str2uword(str string @ AY) -> clobbers() -> (uword @ AY) {
	%asm {{
		;-- convert string (address in A/Y) to uword number in A/Y
		;   @todo don't use the (slow) kernel floating point conversion
		sta  $22
		sty  $23
		jsr  _strlen2233
		tya
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt_FREADSTR			; string to fac1
		jsr  c64flt_GETADR			; fac1 to unsigned word in Y/A
		ldx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPREG
		tya
		ldy  c64.SCRATCH_ZPREG
		rts

_strlen2233
		;-- return the length of the (zero-terminated) string at $22/$23, in Y
		ldy  #0
-		lda  ($22),y
		beq  +
		iny
		bne  -
+		rts
	}}
}

asmsub  str2word(str string @ AY) -> clobbers() -> (word @ AY) {
	%asm {{
		;-- convert string (address in A/Y) to signed word number in A/Y
		;   @todo don't use the (slow) kernel floating point conversion
		sta  $22
		sty  $23
		jsr  str2uword._strlen2233
		tya
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt_FREADSTR		; string to fac1
		jsr  c64flt_FTOSWORDYA		; fac1 to unsigned word in Y/A
		ldx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPREG
		tya
		ldy  c64.SCRATCH_ZPREG
		rts
	}}
}


; @todo string to 32 bit unsigned integer http://www.6502.org/source/strings/ascii-to-32bit.html


asmsub  set_irqvec_excl() -> clobbers(A) -> ()  {
	%asm {{
		sei
		lda  #<_irq_handler
		sta  c64.CINV
		lda  #>_irq_handler
		sta  c64.CINV+1
		cli
		rts
_irq_handler	jsr  irq.irq
		lda  #$ff
		sta  c64.VICIRQ			; acknowledge raster irq
		lda  c64.CIA1ICR		; acknowledge CIA1 interrupt
		jmp  c64.IRQDFEND		; end irq processing - don't call kernel
	}}
}

asmsub  set_irqvec() -> clobbers(A) -> ()  {
	%asm {{
		sei
		lda  #<_irq_handler
		sta  c64.CINV
		lda  #>_irq_handler
		sta  c64.CINV+1
		cli
		rts
_irq_handler    jsr  irq.irq
		jmp  c64.IRQDFRT		; continue with normal kernel irq routine

	}}
}


asmsub  restore_irqvec() -> clobbers() -> () {
	%asm {{
		sei
		lda  #<c64.IRQDFRT
		sta  c64.CINV
		lda  #>c64.IRQDFRT
		sta  c64.CINV+1
		lda  #0
		sta  c64.IREQMASK	; disable raster irq
		lda  #%10000001
		sta  c64.CIA1ICR	; restore CIA1 irq
		cli
		rts
	}}
}


asmsub  set_rasterirq(uword rasterpos @ AY) -> clobbers(A) -> () {
	%asm {{
		sei
		jsr  _setup_raster_irq
		lda  #<_raster_irq_handler
		sta  c64.CINV
		lda  #>_raster_irq_handler
		sta  c64.CINV+1
		cli
		rts

_raster_irq_handler
		jsr  irq.irq
		lda  #$ff
		sta  c64.VICIRQ			; acknowledge raster irq
		jmp  c64.IRQDFRT

_setup_raster_irq
		pha
		lda  #%01111111
		sta  c64.CIA1ICR    ; "switch off" interrupts signals from cia-1
		sta  c64.CIA2ICR    ; "switch off" interrupts signals from cia-2
		and  c64.SCROLY
		sta  c64.SCROLY     ; clear most significant bit of raster position
		lda  c64.CIA1ICR    ; ack previous irq
		lda  c64.CIA2ICR    ; ack previous irq
		pla
		sta  c64.RASTER     ; set the raster line number where interrupt should occur
		cpy  #0
		beq  +
		lda  c64.SCROLY
		ora  #%10000000
		sta  c64.SCROLY     ; set most significant bit of raster position
+		lda  #%00000001
		sta  c64.IREQMASK   ;enable raster interrupt signals from vic
		rts
	}}
}

asmsub  set_rasterirq_excl(uword rasterpos @ AY) -> clobbers(A) -> () {
	%asm {{
		sei
		jsr  set_rasterirq._setup_raster_irq
		lda  #<_raster_irq_handler
		sta  c64.CINV
		lda  #>_raster_irq_handler
		sta  c64.CINV+1
		cli
		rts

_raster_irq_handler
		jsr  irq.irq
		lda  #$ff
		sta  c64.VICIRQ			; acknowledge raster irq
		jmp  c64.IRQDFEND		; end irq processing - don't call kernel

	}}
}



}  ; ------ end of block c64utils





~ c64scr {
	; ---- this block contains (character) Screen and text I/O related functions ----


asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) -> clobbers(A) -> ()  {
	; ---- clear the character screen with the given fill character and character color.
	;      (assumes screen and color matrix are at their default addresses)

	%asm {{
		pha
		tya
		jsr  clear_screencolors
		pla
		jsr  clear_screenchars
		rts
        }}

}


asmsub  clear_screenchars (ubyte char @ A) -> clobbers(Y) -> ()  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
		ldy  #0
_loop		sta  c64.Screen,y
		sta  c64.Screen+1,y
		sta  c64.Screen+$0100,y
		sta  c64.Screen+$0101,y
		sta  c64.Screen+$0200,y
		sta  c64.Screen+$0201,y
		sta  c64.Screen+$02e8,y
		sta  c64.Screen+$02e9,y
		iny
		iny
		bne  _loop
		rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) -> clobbers(Y) -> ()  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
		ldy  #0
_loop		sta  c64.Colors,y
		sta  c64.Colors+1,y
		sta  c64.Colors+$0100,y
		sta  c64.Colors+$0101,y
		sta  c64.Colors+$0200,y
		sta  c64.Colors+$0201,y
		sta  c64.Colors+$02e8,y
		sta  c64.Colors+$02e9,y
		iny
		iny
		bne  _loop
		rts
        }}
}


asmsub scroll_left_full  (ubyte alsocolors @ Pc) -> clobbers(A, Y) -> ()  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx  #0
		ldy  #38
-
	.for row=0, row<=12, row+=1
		lda  c64.Colors + 40*row + 1,x
		sta  c64.Colors + 40*row,x
	.next
		inx
		dey
		bpl  -

		ldx  #0
		ldy  #38
-
	.for row=13, row<=24, row+=1
		lda  c64.Colors + 40*row + 1,x
		sta  c64.Colors + 40*row,x
	.next
		inx
		dey
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx  #0
		ldy  #38
-
	.for row=0, row<=12, row+=1
		lda  c64.Screen + 40*row + 1,x
		sta  c64.Screen + 40*row,x
	.next
		inx
		dey
		bpl  -

		ldx  #0
		ldy  #38
-
	.for row=13, row<=24, row+=1
		lda  c64.Screen + 40*row + 1,x
		sta  c64.Screen + 40*row,x
	.next
		inx
		dey
		bpl  -

		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub scroll_right_full  (ubyte alsocolors @ Pc) -> clobbers(A) -> ()  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx  #38
-
	.for row=0, row<=12, row+=1
		lda  c64.Colors + 40*row + 0,x
		sta  c64.Colors + 40*row + 1,x
	.next
		dex
		bpl  -

		ldx  #38
-
	.for row=13, row<=24, row+=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*row + 1,x
	.next
		dex
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx  #38
-
	.for row=0, row<=12, row+=1
		lda  c64.Screen + 40*row + 0,x
		sta  c64.Screen + 40*row + 1,x
	.next
		dex
		bpl  -

		ldx  #38
-
	.for row=13, row<=24, row+=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*row + 1,x
	.next
		dex
		bpl  -

		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub scroll_up_full  (ubyte alsocolors @ Pc) -> clobbers(A) -> ()  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx #39
-
	.for row=1, row<=11, row+=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*(row-1),x
	.next
		dex
		bpl  -

		ldx #39
-
	.for row=12, row<=24, row+=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*(row-1),x
	.next
		dex
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx #39
-
	.for row=1, row<=11, row+=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*(row-1),x
	.next
		dex
		bpl  -

		ldx #39
-
	.for row=12, row<=24, row+=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*(row-1),x
	.next
		dex
		bpl  -

		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub scroll_down_full  (ubyte alsocolors @ Pc) -> clobbers(A) -> ()  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx #39
-
	.for row=23, row>=12, row-=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*(row+1),x
	.next
		dex
		bpl  -

		ldx #39
-
	.for row=11, row>=0, row-=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*(row+1),x
	.next
		dex
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx #39
-
	.for row=23, row>=12, row-=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*(row+1),x
	.next
		dex
		bpl  -

		ldx #39
-
	.for row=11, row>=0, row-=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*(row+1),x
	.next
		dex
		bpl  -

		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}



asmsub  print (str text @ AY) -> clobbers(A,Y) -> ()  {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to c64.CHROUT of that single char.
	%asm {{
		sta  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		ldy  #0
-		lda  (c64.SCRATCH_ZPB1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		rts
	}}
}


asmsub  print_p  (str_p text @ AY) -> clobbers(A) -> (ubyte @ Y)  {
	; ---- print pstring (length as first byte) from A/Y, returns str len in Y
	%asm {{
		sta  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		stx  c64.SCRATCH_ZPREGX
		ldy  #0
		lda  (c64.SCRATCH_ZPB1),y
		beq  +
		tax
-		iny
		lda  (c64.SCRATCH_ZPB1),y
		jsr  c64.CHROUT
		dex
		bne  -
+		ldx  c64.SCRATCH_ZPREGX
		rts 		; output string length is in Y
	}}
}


asmsub  print_ub0  (ubyte value @ A) -> clobbers(A,Y) -> ()  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		jsr  c64utils.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub  print_ub  (ubyte value @ A) -> clobbers(A,Y) -> ()  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		jsr  c64utils.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		bne  _print_hundreds
		cpx  #'0'
		bne  _print_tens
		jmp  _end
_print_hundreds	tya
		jsr  c64.CHROUT
_print_tens	txa
		jsr  c64.CHROUT
_end		pla
		jsr  c64.CHROUT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}

asmsub  print_b  (byte value @ A) -> clobbers(A,Y) -> ()  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  c64.CHROUT
+		pla
		jsr  c64utils.byte2decimal
		jsr  print_ub._print_byte_digits
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub  print_ubhex  (ubyte prefix @ Pc, ubyte value @ A) -> clobbers(A,Y) -> ()  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  c64utils.ubyte2hex
		jsr  c64.CHROUT
		tya
		jsr  c64.CHROUT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub  print_ubbin  (ubyte prefix @ Pc, ubyte value @ A) -> clobbers(A,Y) ->()  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPB1
		bcc  +
		lda  #'%'
		jsr  c64.CHROUT
+		ldy  #8
-		lda  #'0'
		asl  c64.SCRATCH_ZPB1
		bcc  +
		lda  #'1'
+		jsr  c64.CHROUT
		dey
		bne  -
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


asmsub  print_uwbin  (ubyte prefix @ Pc, uword value @ AY) -> clobbers(A,Y) ->()  {
	; ---- print the uword in A/Y in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		pha
		tya
		jsr  print_ubbin
		pla
		clc
		jmp  print_ubbin
	}}
}


asmsub print_uwhex  (ubyte prefix @ Pc, uword value @ AY) -> clobbers(A,Y) -> ()  {
	; ---- print the uword in A/Y in hexadecimal form (4 digits)
	;      (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		pha
		tya
		jsr  print_ubhex
		pla
		clc
		jmp  print_ubhex
	}}
}


asmsub  print_uw0  (uword value @ AY) -> clobbers(A,Y) -> ()  {
	; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
	%asm {{
		jsr  c64utils.uword2decimal
		ldy  #0
-		lda  c64utils.word2decimal_output,y
		jsr  c64.CHROUT
		iny
		cpy  #5
		bne  -
		rts
	}}
}


asmsub  print_uw  (uword value @ AY) -> clobbers(A,Y) -> ()  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
		jsr  c64utils.uword2decimal
		ldy  #0
		lda  c64utils.word2decimal_output
		cmp  #'0'
		bne  _pr_decimal
		iny
		lda  c64utils.word2decimal_output+1
		cmp  #'0'
		bne  _pr_decimal
		iny
		lda  c64utils.word2decimal_output+2
		cmp  #'0'
		bne  _pr_decimal
		iny
		lda  c64utils.word2decimal_output+3
		cmp  #'0'
		bne  _pr_decimal
		iny

_pr_decimal
		lda  c64utils.word2decimal_output,y
		jsr  c64.CHROUT
		iny
		cpy  #5
		bcc  _pr_decimal
		rts
	}}
}

asmsub  print_w  (word value @ AY) -> clobbers(A,Y) -> ()  {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0s
	%asm {{
		cpy  #0
		bpl  +
		pha
		lda  #'-'
		jsr  c64.CHROUT
		tya
		eor  #255
		tay
		pla
		eor  #255
		clc
		adc  #1
		bcc  +
		iny
+		jmp  print_uw
	}}
}

asmsub  input_chars  (uword buffer @ AY) -> clobbers(A) -> (ubyte @ Y)  {
	; ---- Input a string (max. 80 chars) from the keyboard. Returns length in Y.
	;      It assumes the keyboard is selected as I/O channel!

	%asm {{
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #0				; char counter = 0
-		jsr  c64.CHRIN
		cmp  #$0d			; return (ascii 13) pressed?
		beq  +				; yes, end.
		sta  (c64.SCRATCH_ZPWORD1),y	; else store char in buffer
		iny
		bne  -
+		lda  #0
		sta  (c64.SCRATCH_ZPWORD1),y	; finish string with 0 byte
		rts

	}}
}

asmsub  setchr  (ubyte col @Y, ubyte row @A) -> clobbers(A) -> ()  {
	; ---- set the character in SCRATCH_ZPB1 on the screen matrix at the given position
	%asm {{
		sty  c64.SCRATCH_ZPREG
		asl  a
		tay
		lda  _screenrows+1,y
		sta  _mod+2
		lda  _screenrows,y
		clc
		adc  c64.SCRATCH_ZPREG
		sta  _mod+1
		bcc  +
		inc  _mod+2
+		lda  c64.SCRATCH_ZPB1
_mod		sta  $ffff		; modified
		rts

_screenrows	.word  $0400 + range(0, 1000, 40)
	}}
}

asmsub  getchr  (ubyte col @Y, ubyte row @A) -> clobbers(Y) -> (ubyte @ A) {
	; ---- get the character in the screen matrix at the given location
	%asm  {{
		sty  c64.SCRATCH_ZPB1
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _mod+2
		lda  setchr._screenrows,y
		clc
		adc  c64.SCRATCH_ZPB1
		sta  _mod+1
		bcc  _mod
		inc  _mod+2
_mod		lda  $ffff		; modified
		rts		
	}}
}

asmsub  setclr  (ubyte col @Y, ubyte row @A) -> clobbers(A) -> ()  {
	; ---- set the color in SCRATCH_ZPB1 on the screen matrix at the given position
	%asm {{
		sty  c64.SCRATCH_ZPREG
		asl  a
		tay
		lda  _colorrows+1,y
		sta  _mod+2
		lda  _colorrows,y
		clc
		adc  c64.SCRATCH_ZPREG
		sta  _mod+1
		bcc  +
		inc  _mod+2
+		lda  c64.SCRATCH_ZPB1
_mod		sta  $ffff		; modified
		rts

_colorrows	.word  $d800 + range(0, 1000, 40)
	}}
}

asmsub  getclr  (ubyte col @Y, ubyte row @A) -> clobbers(Y) -> (ubyte @ A) {
	; ---- get the color in the screen color matrix at the given location
	%asm  {{
		sty  c64.SCRATCH_ZPB1
		asl  a
		tay
		lda  setclr._colorrows+1,y
		sta  _mod+2
		lda  setclr._colorrows,y
		clc
		adc  c64.SCRATCH_ZPB1
		sta  _mod+1
		bcc  _mod
		inc  _mod+2
_mod		lda  $ffff		; modified
		rts		
	}}
}

sub  setcc  (ubyte column, ubyte row, ubyte char, ubyte color)  {
	; ---- set char+color at the given position on the screen
	%asm {{
		lda  setcc_row
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _charmod+2
		adc  #$d4
		sta  _colormod+2
		lda  setchr._screenrows,y
		clc
		adc  setcc_column
		sta  _charmod+1
		sta  _colormod+1
		bcc  +
		inc  _charmod+2
		inc  _colormod+2
+		lda  setcc_char
_charmod	sta  $ffff		; modified
		lda  setcc_color
_colormod	sta  $ffff		; modified
		rts
	}}
}

asmsub  PLOT  (ubyte col @ Y, ubyte row @ A) -> clobbers(A) -> () {
	; ---- safe wrapper around PLOT kernel routine, to save the X register.
	%asm  {{
		stx  c64.SCRATCH_ZPREGX
		tax
		clc
		jsr  c64.PLOT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


}  ; ---- end block c64scr
