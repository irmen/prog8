; Prog8 integer math library for 6502
; (floating point math is done via the C-64's BASIC ROM routines)
;
;  some more interesting routines can be found here:
;	http://6502org.wikidot.com/software-math
;	http://codebase64.org/doku.php?id=base:6502_6510_maths
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ math {
		; note: the following ZP scratch registers must be the same as in c64lib
		memory  byte  SCRATCH_ZP1	= $02		; scratch register #1 in ZP
		memory  byte  SCRATCH_ZP2	= $03		; scratch register #2 in ZP
		memory  word  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  word  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)



sub  multiply_bytes  (byte1: X, byte2: Y) -> (A, X?)  {
	; ---- multiply 2 bytes, result as byte in A  (signed or unsigned)
	%asm {{
		stx  SCRATCH_ZP1
		sty  SCRATCH_ZP2
		ldx  #8
-               asl  a
		asl  SCRATCH_ZP1
		bcc  +
		clc
		adc  SCRATCH_ZP2
+               dex
		bne  -
		rts
	}}
}


sub  multiply_bytes_16  (byte1: X, byte2: Y) -> (A?, XY)  {
	; ---- multiply 2 bytes, result as word in X/Y (unsigned)
	%asm {{
		lda  #0
_m_with_add	stx  SCRATCH_ZP1
		sty  SCRATCH_ZP2
		ldx  #8
		lsr  SCRATCH_ZP1
-               bcc  +
		clc
		adc  SCRATCH_ZP2
+               ror  a
		ror  SCRATCH_ZP1
		dex
		bne  -
		tay
		ldx  SCRATCH_ZP1
		rts
	}}
}

sub  multiply_bytes_addA_16  (byte1: X, byte2: Y, add: A) -> (A?, XY)  {
	; ---- multiply 2 bytes and add A, result as word in X/Y (unsigned)
	%asm {{
		jmp  multiply_bytes_16._m_with_add
	}}
}

	word[2]  multiply_words_product = 0
sub  multiply_words  (number: XY) -> (?)  {
	; ---- multiply two 16-bit words into a 32-bit result
	;      input: X/Y = first 16-bit number, SCRATCH_ZPWORD1 in ZP = second 16-bit number
	;      output: multiply_words_product  32-bits product, LSB order (low-to-high)

	%asm {{
		stx  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1

mult16		lda  #$00
		sta  multiply_words_product+2			; clear upper bits of product
		sta  multiply_words_product+3
		ldx  #16			; for all 16 bits...
-	 	lsr  SCRATCH_ZPWORD1+1		; divide multiplier by 2
		ror  SCRATCH_ZPWORD1
		bcc  +
		lda  multiply_words_product+2			; get upper half of product and add multiplicand
		clc
		adc  SCRATCH_ZPWORD2
		sta  multiply_words_product+2
		lda  multiply_words_product+3
		adc  SCRATCH_ZPWORD2+1
+ 		ror  a				; rotate partial product
		sta  multiply_words_product+3
		ror  multiply_words_product+2
		ror  multiply_words_product+1
		ror  multiply_words_product
		dex
		bne  -
		rts
	}}
}


sub  divmod_bytes  (number: X, divisor: Y) -> (X, A)  {
	; ---- divide X by Y, result quotient in X, remainder in A   (unsigned)
	;      division by zero will result in quotient = 255 and remainder = original number
	%asm {{
		stx  SCRATCH_ZP1
		sty  SCRATCH_ZP2

		lda  #0
		ldx  #8
		asl  SCRATCH_ZP1
-		rol  a
		cmp  SCRATCH_ZP2
		bcc  +
		sbc  SCRATCH_ZP2
+		rol  SCRATCH_ZP1
		dex
		bne  -

		ldx  SCRATCH_ZP1
		rts
	}}
}

sub  divmod_words  (divisor: XY) -> (A?, XY)  {
	; ---- divide two words (16 bit each) into 16 bit results
	;      input:  SCRATCH_ZPWORD1 in ZP: 16 bit number, X/Y: 16 bit divisor
	;      output: SCRATCH_ZPWORD1 in ZP: 16 bit result, X/Y: 16 bit remainder
	;      division by zero will result in quotient = 65535 and remainder = divident

	%asm {{
remainder = SCRATCH_ZP1

		stx  SCRATCH_ZPWORD2
		sty  SCRATCH_ZPWORD2+1
		lda  #0	        		;preset remainder to 0
		sta  remainder
		sta  remainder+1
		ldx  #16	        	;repeat for each bit: ...

-		asl  SCRATCH_ZPWORD1		;number lb & hb*2, msb -> Carry
		rol  SCRATCH_ZPWORD1+1
		rol  remainder			;remainder lb & hb * 2 + msb from carry
		rol  remainder+1
		lda  remainder
		sec
		sbc  SCRATCH_ZPWORD2		;substract divisor to see if it fits in
		tay	        		;lb result -> Y, for we may need it later
		lda  remainder+1
		sbc  SCRATCH_ZPWORD2+1
		bcc  +				;if carry=0 then divisor didn't fit in yet

		sta  remainder+1		;else save substraction result as new remainder,
		sty  remainder
		inc  SCRATCH_ZPWORD1		;and INCrement result cause divisor fit in 1 times

+		dex
		bne  -

		lda  remainder			; copy remainder to ZPWORD2 result register
		sta  SCRATCH_ZPWORD2
		lda  remainder+1
		sta  SCRATCH_ZPWORD2+1

		ldx  SCRATCH_ZPWORD1		; load division result in X/Y
		ldy  SCRATCH_ZPWORD1+1

		rts

	}}
}


sub  randbyte  () -> (A)  {
	; ---- 8-bit pseudo random number generator into A

	%asm {{
		lda  _seed
		beq  +
		asl  a
		beq  ++		;if the input was $80, skip the EOR
		bcc  ++
+		eor  _magic	; #$1d		; could be self-modifying code to set new magic
+		sta  _seed
		rts

		_seed		.byte  $3a
_magic		.byte  $1d
_magiceors	.byte  $1d, $2b, $2d, $4d, $5f, $63, $65, $69
		.byte  $71, $87, $8d, $a9, $c3, $cf, $e7, $f5

 		;returns  - this comment avoids compiler warning
	}}
}

sub  randword  () ->  (XY)  {
	; ---- 16 bit pseudo random number generator into XY

	%asm {{
		lda  _seed
		beq  _lowZero	; $0000 and $8000 are special values to test for

 		; Do a normal shift
		asl  _seed
		lda  _seed+1
		rol  a
		bcc  _noEor

_doEor		; high byte is in A
		eor  _magic+1	; #>magic	; could be self-modifying code to set new magic
  		sta  _seed+1
  		lda  _seed
  		eor  _magic	; #<magic	; could be self-modifying code to set new magic
  		sta  _seed
  		tax
  		ldy  _seed+1
  		rts

_lowZero	lda  _seed+1
		beq  _doEor	; High byte is also zero, so apply the EOR
				; For speed, you could store 'magic' into 'seed' directly
				; instead of running the EORs

 		; wasn't zero, check for $8000
		asl  a
		beq  _noEor	; if $00 is left after the shift, then it was $80
		bcs  _doEor	; else, do the EOR based on the carry bit as usual

_noEor		sta  _seed+1
		tay
		ldx  _seed
 		rts


_seed		.word	$2c9e
_magic		.word   $3f1d
_magiceors	.word   $3f1d, $3f81, $3fa5, $3fc5, $4075, $409d, $40cd, $4109
 		.word   $413f, $414b, $4153, $4159, $4193, $4199, $41af, $41bb

 		;returns  - this comment avoids compiler warning
	}}
}


}
