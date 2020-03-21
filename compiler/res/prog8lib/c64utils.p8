; Prog8 definitions for the Commodore-64
; These are the utility subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


%import c64lib


c64utils {

		const   uword  ESTACK_LO	= $ce00
		const   uword  ESTACK_HI	= $cf00


; ----- number conversions to decimal strings

asmsub  ubyte2decimal  (ubyte value @ A) -> ubyte @ Y, ubyte @ A, ubyte @ X  {
	; ---- A to decimal string in Y/A/X  (100s in Y, 10s in A, 1s in X)
    %asm {{
        ldy  #uword2decimal.ASCII_0_OFFSET
        bne  uword2decimal.hex_try200
        rts
	}}
}

asmsub  uword2decimal  (uword value @ AY) -> ubyte @Y, ubyte @A, ubyte @X  {
    ;  ---- convert 16 bit uword in A/Y to decimal
    ;  output in uword2decimal.decTenThousands, decThousands, decHundreds, decTens, decOnes
    ;  (these are terminated by a zero byte so they can be easily printed)
    ;  also returns Y = 100's, A = 10's, X = 1's

    %asm {{

;Convert 16 bit Hex to Decimal (0-65535) Rev 2
;By Omegamatrix    Further optimizations by tepples
; routine from http://forums.nesdev.com/viewtopic.php?f=2&t=11341&start=15

;HexToDec99
; start in A
; end with A = 10's, decOnes (also in X)

;HexToDec255
; start in A
; end with Y = 100's, A = 10's, decOnes (also in X)

;HexToDec999
; start with A = high byte, Y = low byte
; end with Y = 100's, A = 10's, decOnes (also in X)
; requires 1 extra temp register on top of decOnes, could combine
; these two if HexToDec65535 was eliminated...

;HexToDec65535
; start with A/Y (low/high) as 16 bit value
; end with decTenThousand, decThousand, Y = 100's, A = 10's, decOnes (also in X)
; (irmen: I store Y and A in decHundreds and decTens too, so all of it can be easily printed)


ASCII_0_OFFSET 	= $30
temp       	    = c64.SCRATCH_ZPB1	; byte in zeropage
hexHigh      	= c64.SCRATCH_ZPWORD1	; byte in zeropage
hexLow       	= c64.SCRATCH_ZPWORD1+1	; byte in zeropage


HexToDec65535; SUBROUTINE
    sty    hexHigh               ;3  @9
    sta    hexLow                ;3  @12
    tya
    tax                          ;2  @14
    lsr    a                     ;2  @16
    lsr    a                     ;2  @18   integer divide 1024 (result 0-63)

    cpx    #$A7                  ;2  @20   account for overflow of multiplying 24 from 43,000 ($A7F8) onward,
    adc    #1                    ;2  @22   we can just round it to $A700, and the divide by 1024 is fine...

    ;at this point we have a number 1-65 that we have to times by 24,
    ;add to original sum, and Mod 1024 to get a remainder 0-999


    sta    temp                  ;3  @25
    asl    a                     ;2  @27
    adc    temp                  ;3  @30  x3
    tay                          ;2  @32
    lsr    a                     ;2  @34
    lsr    a                     ;2  @36
    lsr    a                     ;2  @38
    lsr    a                     ;2  @40
    lsr    a                     ;2  @42
    tax                          ;2  @44
    tya                          ;2  @46
    asl    a                     ;2  @48
    asl    a                     ;2  @50
    asl    a                     ;2  @52
    clc                          ;2  @54
    adc    hexLow                ;3  @57
    sta    hexLow                ;3  @60
    txa                          ;2  @62
    adc    hexHigh               ;3  @65
    sta    hexHigh               ;3  @68
    ror    a                     ;2  @70
    lsr    a                     ;2  @72
    tay                          ;2  @74    integer divide 1,000 (result 0-65)

    lsr    a                     ;2  @76    split the 1,000 and 10,000 digit
    tax                          ;2  @78
    lda    ShiftedBcdTab,x       ;4  @82
    tax                          ;2  @84
    rol    a                     ;2  @86
    and    #$0F                  ;2  @88
    ora    #ASCII_0_OFFSET
    sta    decThousands          ;3  @91
    txa                          ;2  @93
    lsr    a                     ;2  @95
    lsr    a                     ;2  @97
    lsr    a                     ;2  @99
    ora    #ASCII_0_OFFSET
    sta    decTenThousands       ;3  @102

    lda    hexLow                ;3  @105
    cpy    temp                  ;3  @108
    bmi    _doSubtract           ;2³ @110/111
    beq    _useZero               ;2³ @112/113
    adc    #23 + 24              ;2  @114
_doSubtract
    sbc    #23                   ;2  @116
    sta    hexLow                ;3  @119
_useZero
    lda    hexHigh               ;3  @122
    sbc    #0                    ;2  @124

Start100s
    and    #$03                  ;2  @126
    tax                          ;2  @128   0,1,2,3
    cmp    #2                    ;2  @130
    rol    a                     ;2  @132   0,2,5,7
    ora    #ASCII_0_OFFSET
    tay                          ;2  @134   Y = Hundreds digit

    lda    hexLow                ;3  @137
    adc    Mod100Tab,x           ;4  @141    adding remainder of 256, 512, and 256+512 (all mod 100)
    bcs    hex_doSub200             ;2³ @143/144

hex_try200
    cmp    #200                  ;2  @145
    bcc    hex_try100               ;2³ @147/148
hex_doSub200
    iny                          ;2  @149
    iny                          ;2  @151
    sbc    #200                  ;2  @153
hex_try100
    cmp    #100                  ;2  @155
    bcc    HexToDec99            ;2³ @157/158
    iny                          ;2  @159
    sbc    #100                  ;2  @161

HexToDec99; SUBROUTINE
    lsr    a                     ;2  @163
    tax                          ;2  @165
    lda    ShiftedBcdTab,x       ;4  @169
    tax                          ;2  @171
    rol    a                     ;2  @173
    and    #$0F                  ;2  @175
    ora    #ASCII_0_OFFSET
    sta    decOnes               ;3  @178
    txa                          ;2  @180
    lsr    a                     ;2  @182
    lsr    a                     ;2  @184
    lsr    a                     ;2  @186
    ora    #ASCII_0_OFFSET

    ; irmen: load X with ones, and store Y and A too, for easy printing afterwards
    sty  decHundreds
    sta  decTens
    ldx  decOnes
    rts                          ;6  @192   Y=hundreds, A = tens digit, X=ones digit


HexToDec999; SUBROUTINE
    sty    hexLow                ;3  @9
    jmp    Start100s             ;3  @12

Mod100Tab
    .byte 0,56,12,56+12

ShiftedBcdTab
    .byte $00,$01,$02,$03,$04,$08,$09,$0A,$0B,$0C
    .byte $10,$11,$12,$13,$14,$18,$19,$1A,$1B,$1C
    .byte $20,$21,$22,$23,$24,$28,$29,$2A,$2B,$2C
    .byte $30,$31,$32,$33,$34,$38,$39,$3A,$3B,$3C
    .byte $40,$41,$42,$43,$44,$48,$49,$4A,$4B,$4C

decTenThousands   	.byte  0
decThousands    	.byte  0
decHundreds		.byte  0
decTens			.byte  0
decOnes   		.byte  0
			.byte  0		; zero-terminate the decimal output string

    }}
}


; ----- utility functions ----


asmsub  byte2decimal  (byte value @ A) -> ubyte @ Y, ubyte @ A, ubyte @ X  {
	; ---- A (signed byte) to decimal string in Y/A/X  (100s in Y, 10s in A, 1s in X)
	;      note: if the number is negative, you have to deal with the '-' yourself!
	%asm {{
		cmp  #0
		bpl  +
		eor  #255
		clc
		adc  #1
+		jmp  ubyte2decimal
	}}
}

asmsub  ubyte2hex  (ubyte value @ A) -> ubyte @ A, ubyte @ Y  {
	; ---- A to hex petscii string in AY (first hex char in A, second hex char in Y)
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		pha
		and  #$0f
		tax
		ldy  _hex_digits,x
		pla
		lsr  a
		lsr  a
		lsr  a
		lsr  a
		tax
		lda  _hex_digits,x
		ldx  c64.SCRATCH_ZPREGX
		rts

_hex_digits	.text "0123456789abcdef"	; can probably be reused for other stuff as well
	}}
}

asmsub  uword2hex  (uword value @ AY) clobbers(A,Y)  {
	; ---- convert 16 bit uword in A/Y into 4-character hexadecimal string 'uword2hex.output' (0-terminated)
	%asm {{
		sta  c64.SCRATCH_ZPREG
		tya
		jsr  ubyte2hex
		sta  output
		sty  output+1
		lda  c64.SCRATCH_ZPREG
		jsr  ubyte2hex
		sta  output+2
		sty  output+3
		rts
output	.text  "0000", $00      ; 0-terminated output buffer (to make printing easier)
	}}
}

asmsub str2uword(str string @ AY) -> uword @ AY {
	; -- returns the unsigned word value of the string number argument in AY
	;    the number may NOT be preceded by a + sign and may NOT contain spaces
	;    (any non-digit character will terminate the number string that is parsed)
	%asm {{
_result = c64.SCRATCH_ZPWORD2
		sta  _mod+1
		sty  _mod+2
		ldy  #0
		sty  _result
		sty  _result+1
_mod		lda  $ffff,y		; modified
		sec
		sbc  #48
		bpl  +
_done		; return result
		lda  _result
		ldy  _result+1
		rts
+		cmp  #10
		bcs  _done
		; add digit to result
		pha
		jsr  _result_times_10
		pla
		clc
		adc  _result
		sta  _result
		bcc  +
		inc  _result+1
+		iny
		bne  _mod
		; never reached

_result_times_10     ; (W*4 + W)*2
		lda  _result+1
		sta  c64.SCRATCH_ZPREG
		lda  _result
		asl  a
		rol  c64.SCRATCH_ZPREG
		asl  a
		rol  c64.SCRATCH_ZPREG
		clc
		adc  _result
		sta  _result
		lda  c64.SCRATCH_ZPREG
		adc  _result+1
		asl  _result
		rol  a
		sta  _result+1
		rts
	}}
}

asmsub str2word(str string @ AY) -> word @ AY {
	; -- returns the signed word value of the string number argument in AY
	;    the number may be preceded by a + or - sign but may NOT contain spaces
	;    (any non-digit character will terminate the number string that is parsed)
	%asm {{
_result = c64.SCRATCH_ZPWORD2
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #0
		sty  _result
		sty  _result+1
		sty  _negative
		lda  (c64.SCRATCH_ZPWORD1),y
		cmp  #'+'
		bne  +
		iny
+		cmp  #'-'
		bne  _parse
		inc  _negative
		iny
_parse		lda  (c64.SCRATCH_ZPWORD1),y
		sec
		sbc  #48
		bpl  _digit
_done		; return result
		lda  _negative
		beq  +
		sec
		lda  #0
		sbc  _result
		sta  _result
		lda  #0
		sbc  _result+1
		sta  _result+1
+		lda  _result
		ldy  _result+1
		rts
_digit		cmp  #10
		bcs  _done
		; add digit to result
		pha
		jsr  str2uword._result_times_10
		pla
		clc
		adc  _result
		sta  _result
		bcc  +
		inc  _result+1
+		iny
		bne  _parse
		; never reached
_negative	.byte  0
	}}
}

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
		lda  c64.SCRATCH_ZPB1
		sta  IRQ_SCRATCH_ZPB1
		lda  c64.SCRATCH_ZPREG
		sta  IRQ_SCRATCH_ZPREG
		lda  c64.SCRATCH_ZPREGX
		sta  IRQ_SCRATCH_ZPREGX
		lda  c64.SCRATCH_ZPWORD1
		sta  IRQ_SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
		sta  IRQ_SCRATCH_ZPWORD1+1
		lda  c64.SCRATCH_ZPWORD2
		sta  IRQ_SCRATCH_ZPWORD2
		lda  c64.SCRATCH_ZPWORD2+1
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
		sta  c64.SCRATCH_ZPB1
		lda  IRQ_SCRATCH_ZPREG
		sta  c64.SCRATCH_ZPREG
		lda  IRQ_SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPREGX
		lda  IRQ_SCRATCH_ZPWORD1
		sta  c64.SCRATCH_ZPWORD1
		lda  IRQ_SCRATCH_ZPWORD1+1
		sta  c64.SCRATCH_ZPWORD1+1
		lda  IRQ_SCRATCH_ZPWORD2
		sta  c64.SCRATCH_ZPWORD2
		lda  IRQ_SCRATCH_ZPWORD2+1
		sta  c64.SCRATCH_ZPWORD2+1
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

asmsub  scroll_right_full  (ubyte alsocolors @ Pc) clobbers(A)  {
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

asmsub  scroll_up_full  (ubyte alsocolors @ Pc) clobbers(A)  {
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

asmsub  scroll_down_full  (ubyte alsocolors @ Pc) clobbers(A)  {
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


asmsub  print (str text @ AY) clobbers(A,Y)  {
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

asmsub  print_ub0  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		jsr  c64utils.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		jsr  c64utils.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		beq  +
		tya
		jsr  c64.CHROUT
+       pla
        cmp  #'0'
        beq  +
        jsr  c64.CHROUT
+       txa
		jsr  c64.CHROUT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,Y)  {
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

asmsub  print_ubhex  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
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

asmsub  print_ubbin  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
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

asmsub print_uwhex  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
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
	    stx  c64.SCRATCH_ZPREGX
		jsr  c64utils.uword2decimal
		ldy  #0
-		lda  c64utils.uword2decimal.decTenThousands,y
        beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
	    stx  c64.SCRATCH_ZPREGX
		jsr  c64utils.uword2decimal
		ldx  c64.SCRATCH_ZPREGX
		ldy  #0
-		lda  c64utils.uword2decimal.decTenThousands,y
		beq  _allzero
		cmp  #'0'
		bne  _gotdigit
		iny
		bne  -

_gotdigit
		jsr  c64.CHROUT
		iny
		lda  c64utils.uword2decimal.decTenThousands,y
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

asmsub  setchr  (ubyte col @Y, ubyte row @A) clobbers(A)  {
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

asmsub  getchr  (ubyte col @Y, ubyte row @A) clobbers(Y) -> ubyte @ A {
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

asmsub  setclr  (ubyte col @Y, ubyte row @A) clobbers(A)  {
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

asmsub  getclr  (ubyte col @Y, ubyte row @A) clobbers(Y) -> ubyte @ A {
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
		stx  c64.SCRATCH_ZPREGX
		tax
		clc
		jsr  c64.PLOT
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}


}  ; ---- end block c64scr
