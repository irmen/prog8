; Prog8 definitions for the Commodore-64
; These are the utility subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


%import c64lib


~ c64utils {

; ----- utility functions ----

asmsub  init_system  () -> clobbers(A,X,Y) -> ()  {
	; ---- initializes the machine to a sane starting state
	; This means that the BASIC, KERNAL and CHARGEN ROMs are banked in,
	; the VIC, SID and CIA chips are reset, screen is cleared, and the default IRQ is set.
	; Also a different color scheme is chosen to identify ourselves a little. 
	; Uppercase charset is activated, and all three registers set to 0, status flags cleared.
	%asm {{
		sei
		cld
		lda  #%00101111
		sta  $00
		lda  #%00100111
		sta  $01
		jsr  c64.IOINIT
		jsr  c64.RESTOR
		jsr  c64.CINT
		lda  #6
		sta  c64.EXTCOL
		lda  #7
		sta  c64.COLOR
		lda  #0
		sta  c64.BGCOL0
		tax
		tay
		clc
		clv
		cli
		rts
	}}
}

asmsub  byte2decimal  (value: ubyte @ A) -> clobbers() -> (ubyte @ Y, ubyte @ X, ubyte @ A)  {
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

asmsub  byte2hex  (value: ubyte @ A) -> clobbers(A) -> (ubyte @ X, ubyte @ Y)  {
	; ---- A to hex string in XY (first hex char in X, second hex char in Y)
	%asm {{
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
		tax
		rts

hex_digits	.text "0123456789abcdef"	; can probably be reused for other stuff as well
	}}
}


		str  word2hex_output = "1234"   ; 0-terminated, to make printing easier
asmsub  word2hex  (dataword: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- convert 16 bit word in X/Y into 4-character hexadecimal string into memory  'word2hex_output'
	%asm {{
		stx  c64.SCRATCH_ZPREG
		tya
		jsr  byte2hex
		stx  word2hex_output
		sty  word2hex_output+1
		lda  c64.SCRATCH_ZPREG
		jsr  byte2hex
		stx  word2hex_output+2
		sty  word2hex_output+3
		rts
	}}
}

		ubyte[3]  word2bcd_bcdbuff = [0, 0, 0]
asmsub  word2bcd  (dataword: uword @ XY) -> clobbers(A,X) -> ()  {
	; Convert an 16 bit binary value to BCD
	;
	; This function converts a 16 bit binary value in X/Y into a 24 bit BCD. It
	; works by transferring one bit a time from the source and adding it
	; into a BCD value that is being doubled on each iteration. As all the
	; arithmetic is being done in BCD the result is a binary to decimal
	; conversion.
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		sed				; switch to decimal mode
		lda  #0				; ensure the result is clear
		sta  word2bcd_bcdbuff+0
		sta  word2bcd_bcdbuff+1
		sta  word2bcd_bcdbuff+2
		ldx  #16			; the number of source bits

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
		dex				; and repeat for next bit
		bne  -
		cld				; back to binary
		rts
	}}
}


		ubyte[5]  word2decimal_output = 0
asmsub  word2decimal  (dataword: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- convert 16 bit word in X/Y into decimal string into memory  'word2decimal_output'
	%asm {{
		jsr  word2bcd
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


; @todo string to 32 bit unsigned integer http://www.6502.org/source/strings/ascii-to-32bit.html


}  ; ------ end of block c64utils


~ c64flt {
	; ---- this block contains C-64 floating point related functions ----


asmsub  FREADS32  () -> clobbers(A,X,Y) -> ()  {
	; ---- fac1 = signed int32 from $62-$65 big endian (MSB FIRST)
	%asm {{
		lda  $62
		eor  #$ff
		asl  a
		lda  #0
		ldx  #$a0
		jmp  $bc4f		; internal BASIC routine
	}}
}

asmsub  FREADUS32  () -> clobbers(A,X,Y) -> ()  {
	; ---- fac1 = uint32 from $62-$65 big endian (MSB FIRST)
	%asm {{
		sec
		lda  #0
		ldx  #$a0
		jmp  $bc4f		; internal BASIC routine
	}}
}

asmsub  FREADS24AXY  (lo: ubyte @ A, mid: ubyte @ X, hi: ubyte @ Y) -> clobbers(A,X,Y) -> ()  {
	; ---- fac1 = signed int24 (A/X/Y contain lo/mid/hi bytes)
	;      note: there is no FREADU24AXY (unsigned), use FREADUS32 instead.
	%asm {{
		sty  $62
		stx  $63
		sta  $64
		lda  $62
		eor  #$FF
		asl  a
		lda  #0
		sta  $65
		ldx  #$98
		jmp  $bc4f		; internal BASIC routine
	}}
}

asmsub  GIVUAYF  (value: uword @ AY) -> clobbers(A,X,Y) -> ()  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	%asm {{
		sty  $62
		sta  $63
		ldx  #$90
		sec
		jmp  $bc49		; internal BASIC routine
	}}
}

asmsub  GIVAYFAY  (value: uword @ AY) -> clobbers(A,X,Y) -> ()  {
	; ---- signed 16 bit word in A/Y (lo/hi) to float in fac1
	%asm {{
		sta  c64.SCRATCH_ZPB1
		tya
		ldy  c64.SCRATCH_ZPB1
		jmp  c64.GIVAYF		; this uses the inverse order, Y/A
	}}
}

asmsub  FTOSWRDAY  () -> clobbers(X) -> (uword @ AY)  {
	; ---- fac1 to signed word in A/Y
	%asm {{
		jsr  c64.FTOSWORDYA	; note the inverse Y/A order
		sta  c64.SCRATCH_ZPB1
		tya
		ldy  c64.SCRATCH_ZPB1
		rts
	}}
}

asmsub  GETADRAY  () -> clobbers(X) -> (uword @ AY)  {
	; ---- fac1 to unsigned word in A/Y
	%asm {{
		jsr  c64.GETADR		; this uses the inverse order, Y/A
		sta  c64.SCRATCH_ZPB1
		tya
		ldy  c64.SCRATCH_ZPB1
		rts
	}}
}


asmsub  copy_mflt  (source: uword @ XY) -> clobbers(A) -> ()  {
	; ---- copy a 5 byte MFLT floating point variable to another place
	;      input: X/Y = source address,  c64.SCRATCH_ZPWORD1 = destination address
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #0
		lda  (c64.SCRATCH_ZPB1),y
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		lda  (c64.SCRATCH_ZPB1),y
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		lda  (c64.SCRATCH_ZPB1),y
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		lda  (c64.SCRATCH_ZPB1),y
		sta  (c64.SCRATCH_ZPWORD1),y
		iny
		lda  (c64.SCRATCH_ZPB1),y
		sta  (c64.SCRATCH_ZPWORD1),y
		ldy  c64.SCRATCH_ZPWORD1+1
		rts
	}}
}

asmsub  float_add_one  (mflt: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- add 1 to the MFLT pointed to by X/Y.  Clobbers A, X, Y
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		txa
		jsr  c64.MOVFM		; fac1 = float XY
		lda  #<c64.FL_FONE
		ldy  #>c64.FL_FONE
		jsr  c64.FADD		; fac1 += 1
		ldx  c64.SCRATCH_ZPB1
		ldy  c64.SCRATCH_ZPREG
		jmp  c64.FTOMEMXY	; float XY = fac1
	}}
}

asmsub  float_sub_one  (mflt: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- subtract 1 from the MFLT pointed to by X/Y.  Clobbers A, X, Y
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		lda  #<c64.FL_FONE
		ldy  #>c64.FL_FONE
		jsr  c64.MOVFM		; fac1 = 1
		txa
		ldy  c64.SCRATCH_ZPREG
		jsr  c64.FSUB		; fac1 = float XY - 1
		ldx  c64.SCRATCH_ZPB1
		ldy  c64.SCRATCH_ZPREG
		jmp  c64.FTOMEMXY	; float XY = fac1
	}}
}

asmsub  float_add_SW1_to_XY  (mflt: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- add MFLT pointed to by SCRATCH_ZPWORD1 to the MFLT pointed to by X/Y.  Clobbers A, X, Y
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		txa
		jsr  c64.MOVFM		; fac1 = float XY
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64.FADD		; fac1 += SCRATCH_ZPWORD1
		ldx  c64.SCRATCH_ZPB1
		ldy  c64.SCRATCH_ZPREG
		jmp  c64.FTOMEMXY	; float XY = fac1
	}}
}

asmsub  float_sub_SW1_from_XY  (mflt: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- subtract MFLT pointed to by SCRATCH_ZPWORD1 from the MFLT pointed to by X/Y.  Clobbers A, X, Y
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64.MOVFM		; fac1 = SCRATCH_ZPWORD1
		txa
		ldy  c64.SCRATCH_ZPREG
		jsr  c64.FSUB		; fac1 = float XY - SCRATCH_ZPWORD1
		ldx  c64.SCRATCH_ZPB1
		ldy  c64.SCRATCH_ZPREG
		jmp  c64.FTOMEMXY	; float XY = fac1
	}}
}

sub  print_float  (value: float) {
	; ---- prints the floating point value (without a newline) using basic rom routines. 
	;      clobbers no registers.
	%asm {{
		pha
		tya
		pha
		txa
		pha
		lda  #<print_float_value
		ldy  #>print_float_value
		jsr  c64.MOVFM		; load float into fac1
		jsr  c64.FOUT		; fac1 to string in A/Y
		jsr  c64.STROUT		; print string in A/Y
		pla
		tax
		pla
		tay
		pla
		rts
	}}
}

sub  print_float_ln  (value: float) {
	; ---- prints the floating point value (with a newline at the end) using basic rom routines
	;      clobbers no registers.
	%asm {{
		pha
		tya
		pha
		txa
		pha
		lda  #<print_float_ln_value
		ldy  #>print_float_ln_value
		jsr  c64.MOVFM		; load float into fac1
		jsr  c64.FPRINTLN	; print fac1 with newline
		pla
		tax
		pla
		tay
		pla
		rts
	}}
        
}

}  ; ------ end of block c64flt



~ c64scr {
	; ---- this block contains (character) Screen and text I/O related functions ----


asmsub  clear_screen (char: ubyte @ A, color: ubyte @ Y) -> clobbers(A,X) -> ()  {
	; ---- clear the character screen with the given fill character and character color.
	;      (assumes screen is at $0400, could be altered in the future with self-modifying code)
	;       @todo some byte var to set the SCREEN ADDR HI BYTE

	%asm {{
		sta  _loop + 1      ; self-modifying
		stx  c64.SCRATCH_ZPB1
		ldx  #0
_loop           lda  #0
		sta  c64.Screen,x
		sta  c64.Screen+$0100,x
		sta  c64.Screen+$0200,x
		sta  c64.Screen+$02e8,x
	        tya
		sta  c64.Colors,x
		sta  c64.Colors+$0100,x
		sta  c64.Colors+$0200,x
		sta  c64.Colors+$02e8,x
		inx
		bne  _loop

		lda  _loop+1		; restore A and X
		ldx  c64.SCRATCH_ZPB1
		rts
        }}

}


asmsub scroll_left_full  (alsocolors: ubyte @ Pc) -> clobbers(A, X, Y) -> ()  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
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

		rts
	}}
}


asmsub scroll_right_full  (alsocolors: ubyte @ Pc) -> clobbers(A,X) -> ()  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
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

		rts
	}}
}


asmsub scroll_up_full  (alsocolors: ubyte @ Pc) -> clobbers(A,X) -> ()  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
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

		rts
	}}
}


asmsub scroll_down_full  (alsocolors: ubyte @ Pc) -> clobbers(A,X) -> ()  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
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

		rts
	}}
}



asmsub  print_string (text: str @ XY) -> clobbers(A,Y) -> ()  {
	; ---- print null terminated string from X/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to c64.CHROUT of that single char.    @todo do this
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		ldy  #0
-               lda  (c64.SCRATCH_ZPB1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		rts
	}}
}


asmsub  print_pstring  (text: str_p @ XY) -> clobbers(A,X) -> (ubyte @ Y)  {
	; ---- print pstring (length as first byte) from X/Y, returns str len in Y
	%asm {{
		stx  c64.SCRATCH_ZPB1
		sty  c64.SCRATCH_ZPREG
		ldy  #0
		lda  (c64.SCRATCH_ZPB1),y
		beq  +
		tax
-		iny
		lda  (c64.SCRATCH_ZPB1),y
		jsr  c64.CHROUT
		dex
		bne  -
+		rts 			; output string length is in Y
	}}
}


asmsub  print_byte_decimal0  (value: ubyte @ A) -> clobbers(A,X,Y) -> ()  {
	; ---- print the byte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		jsr  c64utils.byte2decimal
		pha
		tya
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		pla
		jmp  c64.CHROUT
	}}
}


asmsub  print_byte_decimal  (value: ubyte @ A) -> clobbers(A,X,Y) -> ()  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		jsr  c64utils.byte2decimal
		pha
		cpy  #'0'
		bne  _print_hundreds
		cpx  #'0'
		bne  _print_tens
		pla
		jmp  c64.CHROUT
_print_hundreds	tya
		jsr  c64.CHROUT
_print_tens	txa
		jsr  c64.CHROUT
		pla
		jmp  c64.CHROUT
	}}
}


asmsub  print_byte_hex  (prefix: ubyte @ Pc, value: ubyte @ A) -> clobbers(A,X,Y) -> ()  {
	; ---- print the byte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  c64utils.byte2hex
		txa
		jsr  c64.CHROUT
		tya
		jmp  c64.CHROUT
	}}
}


asmsub print_word_hex  (prefix: ubyte @ Pc, dataword: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- print the (unsigned) word in X/Y in hexadecimal form (4 digits)
	;      (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		stx  c64.SCRATCH_ZPB1
		tya
		jsr  print_byte_hex
		lda  c64.SCRATCH_ZPB1
		clc
		jmp  print_byte_hex
	}}
}


asmsub  print_word_decimal0  (dataword: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- print the (unsigned) word in X/Y in decimal form, with left padding 0s (5 positions total)
	; @todo shorter in loop form?
	%asm {{
		jsr  c64utils.word2decimal
		lda  c64utils.word2decimal_output
		jsr  c64.CHROUT
		lda  c64utils.word2decimal_output+1
		jsr  c64.CHROUT
		lda  c64utils.word2decimal_output+2
		jsr  c64.CHROUT
		lda  c64utils.word2decimal_output+3
		jsr  c64.CHROUT
		lda  c64utils.word2decimal_output+4
		jmp  c64.CHROUT
	}}
}


asmsub  print_word_decimal  (dataword: uword @ XY) -> clobbers(A,X,Y) -> ()  {
	; ---- print the word in X/Y in decimal form, without left padding 0s
	%asm {{
		jsr  c64utils.word2decimal
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


asmsub  input_chars  (buffer: uword @ AY) -> clobbers(A, X) -> (ubyte @ Y)  {
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

}  ; ---- end block c64scr
