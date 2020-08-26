; Prog8 definitions for the Commodore-64
; These are the utility subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


%import c64lib
%import conv


c64utils {

asmsub  set_irqvec_excl() clobbers(A)  {
	%asm {{
		sei
		lda  #<_irq_handler
		sta  c64.CINV
		lda  #>_irq_handler
		sta  c64.CINV+1
		cli
		rts
_irq_handler	jsr  set_irqvec._irq_handler_init
		jsr  irq.irq
		jsr  set_irqvec._irq_handler_end
		lda  #$ff
		sta  c64.VICIRQ			; acknowledge raster irq
		lda  c64.CIA1ICR		; acknowledge CIA1 interrupt
		jmp  c64.IRQDFEND		; end irq processing - don't call kernel
	}}
}

asmsub  set_irqvec() clobbers(A)  {
	%asm {{
		sei
		lda  #<_irq_handler
		sta  c64.CINV
		lda  #>_irq_handler
		sta  c64.CINV+1
		cli
		rts
_irq_handler    jsr  _irq_handler_init
		jsr  irq.irq
		jsr  _irq_handler_end
		jmp  c64.IRQDFRT		; continue with normal kernel irq routine

_irq_handler_init
		; save all zp scratch registers and the X register as these might be clobbered by the irq routine
		stx  IRQ_X_REG
		lda  P8ZP_SCRATCH_B1
		sta  IRQ_SCRATCH_ZPB1
		lda  P8ZP_SCRATCH_REG
		sta  IRQ_SCRATCH_ZPREG
		lda  P8ZP_SCRATCH_REG_X
		sta  IRQ_SCRATCH_ZPREGX
		lda  P8ZP_SCRATCH_W1
		sta  IRQ_SCRATCH_ZPWORD1
		lda  P8ZP_SCRATCH_W1+1
		sta  IRQ_SCRATCH_ZPWORD1+1
		lda  P8ZP_SCRATCH_W2
		sta  IRQ_SCRATCH_ZPWORD2
		lda  P8ZP_SCRATCH_W2+1
		sta  IRQ_SCRATCH_ZPWORD2+1
		; stack protector; make sure we don't clobber the top of the evaluation stack
		dex
		dex
		dex
		dex
		dex
		dex
		cld
		rts

_irq_handler_end
		; restore all zp scratch registers and the X register
		lda  IRQ_SCRATCH_ZPB1
		sta  P8ZP_SCRATCH_B1
		lda  IRQ_SCRATCH_ZPREG
		sta  P8ZP_SCRATCH_REG
		lda  IRQ_SCRATCH_ZPREGX
		sta  P8ZP_SCRATCH_REG_X
		lda  IRQ_SCRATCH_ZPWORD1
		sta  P8ZP_SCRATCH_W1
		lda  IRQ_SCRATCH_ZPWORD1+1
		sta  P8ZP_SCRATCH_W1+1
		lda  IRQ_SCRATCH_ZPWORD2
		sta  P8ZP_SCRATCH_W2
		lda  IRQ_SCRATCH_ZPWORD2+1
		sta  P8ZP_SCRATCH_W2+1
		ldx  IRQ_X_REG
		rts

IRQ_X_REG		.byte  0
IRQ_SCRATCH_ZPB1	.byte  0
IRQ_SCRATCH_ZPREG	.byte  0
IRQ_SCRATCH_ZPREGX	.byte  0
IRQ_SCRATCH_ZPWORD1	.word  0
IRQ_SCRATCH_ZPWORD2	.word  0

		}}
}

asmsub  restore_irqvec() {
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

asmsub  set_rasterirq(uword rasterpos @ AY) clobbers(A) {
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
		jsr  set_irqvec._irq_handler_init
		jsr  irq.irq
		jsr  set_irqvec._irq_handler_end
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

asmsub  set_rasterirq_excl(uword rasterpos @ AY) clobbers(A) {
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
		jsr  set_irqvec._irq_handler_init
		jsr  irq.irq
		jsr  set_irqvec._irq_handler_end
		lda  #$ff
		sta  c64.VICIRQ			; acknowledge raster irq
		jmp  c64.IRQDFEND		; end irq processing - don't call kernel

	}}
}


}  ; ------ end of block c64utils




c64scr {
	; ---- this block contains (character) Screen and text I/O related functions ----


asmsub  clear_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  {
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

asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
		ldy  #0
_loop		sta  c64.Screen,y
		sta  c64.Screen+$0100,y
		sta  c64.Screen+$0200,y
		sta  c64.Screen+$02e8,y
		iny
		bne  _loop
		rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
		ldy  #0
_loop		sta  c64.Colors,y
		sta  c64.Colors+$0100,y
		sta  c64.Colors+$0200,y
		sta  c64.Colors+$02e8,y
		iny
		bne  _loop
		rts
        }}
}

asmsub  scroll_left_full  (ubyte alsocolors @ Pc) clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too

	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx  #0
		ldy  #38
-
	.for row=0, row<=24, row+=1
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
	.for row=0, row<=24, row+=1
		lda  c64.Screen + 40*row + 1,x
		sta  c64.Screen + 40*row,x
	.next
		inx
		dey
		bpl  -

		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  scroll_right_full  (ubyte alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx  #38
-
	.for row=0, row<=24, row+=1
		lda  c64.Colors + 40*row + 0,x
		sta  c64.Colors + 40*row + 1,x
	.next
		dex
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx  #38
-
	.for row=0, row<=24, row+=1
		lda  c64.Screen + 40*row + 0,x
		sta  c64.Screen + 40*row + 1,x
	.next
		dex
		bpl  -

		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  scroll_up_full  (ubyte alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx #39
-
	.for row=1, row<=24, row+=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*(row-1),x
	.next
		dex
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx #39
-
	.for row=1, row<=24, row+=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*(row-1),x
	.next
		dex
		bpl  -

		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  scroll_down_full  (ubyte alsocolors @ Pc) clobbers(A)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	;      Carry flag determines if screen color data must be scrolled too
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		bcs  +
		jmp  _scroll_screen

+               ; scroll the color memory
		ldx #39
-
	.for row=23, row>=0, row-=1
		lda  c64.Colors + 40*row,x
		sta  c64.Colors + 40*(row+1),x
	.next
		dex
		bpl  -

_scroll_screen  ; scroll the screen memory
		ldx #39
-
	.for row=23, row>=0, row-=1
		lda  c64.Screen + 40*row,x
		sta  c64.Screen + 40*(row+1),x
	.next
		dex
		bpl  -

		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}


asmsub  print (str text @ AY) clobbers(A,Y)  {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to c64.CHROUT of that single char.
	%asm {{
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  (P8ZP_SCRATCH_B1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		rts
	}}
}

asmsub  print_ub0  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		jsr  conv.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		beq  +
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		jmp  _ones
+       pla
        cmp  #'0'
        beq  _ones
        jsr  c64.CHROUT
_ones   txa
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,Y)  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  c64.CHROUT
+		pla
		jsr  conv.byte2decimal
		jsr  print_ub._print_byte_digits
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_ubhex  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  conv.ubyte2hex
		jsr  c64.CHROUT
		tya
		jsr  c64.CHROUT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_ubbin  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		sta  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'%'
		jsr  c64.CHROUT
+		ldy  #8
-		lda  #'0'
		asl  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'1'
+		jsr  c64.CHROUT
		dey
		bne  -
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_uwbin  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
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

asmsub  print_uwhex  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
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

asmsub  print_uw0  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
	%asm {{
	    stx  P8ZP_SCRATCH_REG_X
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
	    stx  P8ZP_SCRATCH_REG_X
		jsr  conv.uword2decimal
		ldx  P8ZP_SCRATCH_REG_X
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
		beq  _allzero
		cmp  #'0'
		bne  _gotdigit
		iny
		bne  -

_gotdigit
		jsr  c64.CHROUT
		iny
		lda  conv.uword2decimal.decTenThousands,y
		bne  _gotdigit
		rts
_allzero
        lda  #'0'
        jmp  c64.CHROUT
	}}
}

asmsub  print_w  (word value @ AY) clobbers(A,Y)  {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
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

asmsub  input_chars  (uword buffer @ AY) clobbers(A) -> ubyte @ Y  {
	; ---- Input a string (max. 80 chars) from the keyboard. Returns length in Y. (string is terminated with a 0 byte as well)
	;      It assumes the keyboard is selected as I/O channel!

	%asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0				; char counter = 0
-		jsr  c64.CHRIN
		cmp  #$0d			; return (ascii 13) pressed?
		beq  +				; yes, end.
		sta  (P8ZP_SCRATCH_W1),y	; else store char in buffer
		iny
		bne  -
+		lda  #0
		sta  (P8ZP_SCRATCH_W1),y	; finish string with 0 byte
		rts

	}}
}

asmsub  setchr  (ubyte col @Y, ubyte row @A) clobbers(A)  {
	; ---- set the character in SCRATCH_ZPB1 on the screen matrix at the given position
	%asm {{
		sty  P8ZP_SCRATCH_REG
		asl  a
		tay
		lda  _screenrows+1,y
		sta  _mod+2
		lda  _screenrows,y
		clc
		adc  P8ZP_SCRATCH_REG
		sta  _mod+1
		bcc  +
		inc  _mod+2
+		lda  P8ZP_SCRATCH_B1
_mod		sta  $ffff		; modified
		rts

_screenrows	.word  $0400 + range(0, 1000, 40)
	}}
}

asmsub  getchr  (ubyte col @Y, ubyte row @A) clobbers(Y) -> ubyte @ A {
	; ---- get the character in the screen matrix at the given location
	%asm  {{
		sty  P8ZP_SCRATCH_B1
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _mod+2
		lda  setchr._screenrows,y
		clc
		adc  P8ZP_SCRATCH_B1
		sta  _mod+1
		bcc  _mod
		inc  _mod+2
_mod		lda  $ffff		; modified
		rts
	}}
}

asmsub  setclr  (ubyte col @Y, ubyte row @A) clobbers(A)  {
	; ---- set the color in SCRATCH_ZPB1 on the screen matrix at the given position
	%asm {{
		sty  P8ZP_SCRATCH_REG
		asl  a
		tay
		lda  _colorrows+1,y
		sta  _mod+2
		lda  _colorrows,y
		clc
		adc  P8ZP_SCRATCH_REG
		sta  _mod+1
		bcc  +
		inc  _mod+2
+		lda  P8ZP_SCRATCH_B1
_mod		sta  $ffff		; modified
		rts

_colorrows	.word  $d800 + range(0, 1000, 40)
	}}
}

asmsub  getclr  (ubyte col @Y, ubyte row @A) clobbers(Y) -> ubyte @ A {
	; ---- get the color in the screen color matrix at the given location
	%asm  {{
		sty  P8ZP_SCRATCH_B1
		asl  a
		tay
		lda  setclr._colorrows+1,y
		sta  _mod+2
		lda  setclr._colorrows,y
		clc
		adc  P8ZP_SCRATCH_B1
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
		lda  row
		asl  a
		tay
		lda  setchr._screenrows+1,y
		sta  _charmod+2
		adc  #$d4
		sta  _colormod+2
		lda  setchr._screenrows,y
		clc
		adc  column
		sta  _charmod+1
		sta  _colormod+1
		bcc  +
		inc  _charmod+2
		inc  _colormod+2
+		lda  char
_charmod	sta  $ffff		; modified
		lda  color
_colormod	sta  $ffff		; modified
		rts
	}}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ A) clobbers(A) {
	; ---- safe wrapper around PLOT kernel routine, to save the X register.
	%asm  {{
		stx  P8ZP_SCRATCH_REG_X
		tax
		clc
		jsr  c64.PLOT
		ldx  P8ZP_SCRATCH_REG_X
		rts
	}}
}


}  ; ---- end block c64scr
