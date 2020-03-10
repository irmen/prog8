; Prog8 definitions for floating point handling on the Commodore-64
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%option enable_floats


c64flt {
	; ---- this block contains C-64 floating point related functions ----

		const  float  PI	= 3.141592653589793
		const  float  TWOPI	= 6.283185307179586


; ---- C64 basic and kernal ROM float constants and functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

		; constants in five-byte "mflpt" format in the BASIC ROM
		&float  FL_PIVAL	= $aea8  ; 3.1415926...
		&float  FL_N32768	= $b1a5  ; -32768
		&float  FL_FONE		= $b9bc  ; 1
		&float  FL_SQRHLF	= $b9d6  ; SQR(2) / 2
		&float  FL_SQRTWO	= $b9db  ; SQR(2)
		&float  FL_NEGHLF	= $b9e0  ; -.5
		&float  FL_LOG2		= $b9e5  ; LOG(2)
		&float  FL_TENC		= $baf9  ; 10
		&float  FL_NZMIL	= $bdbd  ; 1e9 (1 billion)
		&float  FL_FHALF	= $bf11  ; .5
		&float  FL_LOGEB2	= $bfbf  ; 1 / LOG(2)
		&float  FL_PIHALF	= $e2e0  ; PI / 2
		&float  FL_TWOPI	= $e2e5  ; 2 * PI
		&float  FL_FR4		= $e2ea  ; .25
		 float  FL_ZERO		= 0.0    ; oddly enough 0.0 isn't available in the kernel


; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

; checked functions below:
asmsub	MOVFM		(uword mflpt @ AY) clobbers(A,Y)	= $bba2		; load mflpt value from memory  in A/Y into fac1
asmsub	FREADMEM	() clobbers(A,Y)			= $bba6		; load mflpt value from memory  in $22/$23 into fac1
asmsub	CONUPK		(uword mflpt @ AY) clobbers(A,Y)	= $ba8c		; load mflpt value from memory  in A/Y into fac2
asmsub	FAREADMEM	() clobbers(A,Y)			= $ba90		; load mflpt value from memory  in $22/$23 into fac2
asmsub	MOVFA		() clobbers(A,X)			= $bbfc		; copy fac2 to fac1
asmsub	MOVAF		() clobbers(A,X)			= $bc0c		; copy fac1 to fac2  (rounded)
asmsub	MOVEF		() clobbers(A,X)			= $bc0f		; copy fac1 to fac2
asmsub	MOVMF		(uword mflpt @ XY) clobbers(A,Y)	= $bbd4		; store fac1 to memory  X/Y as 5-byte mflpt

; fac1-> signed word in Y/A (might throw ILLEGAL QUANTITY)
; (tip: use c64flt.FTOSWRDAY to get A/Y output; lo/hi switched to normal little endian order)
asmsub	FTOSWORDYA	() clobbers(X) -> ubyte @ Y, ubyte @ A  = $b1aa     ; note: calls AYINT.

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use c64flt.GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
asmsub	GETADR		() clobbers(X) -> ubyte @ Y, ubyte @ A  = $b7f7

asmsub	QINT		() clobbers(A,X,Y)			= $bc9b		; fac1 -> 4-byte signed integer in 98-101 ($62-$65), with the MSB FIRST.
asmsub	AYINT		() clobbers(A,X,Y)			= $b1bf		; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; (tip: use c64flt.GIVAYFAY to use A/Y input; lo/hi switched to normal order)
; there is also c64flt.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; there is also c64flt.FREADS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADUS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADS24AXY  that reads signed int24 into fac1 from A/X/Y (lo/mid/hi bytes)
asmsub	GIVAYF		(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)	= $b391

asmsub	FREADUY		(ubyte value @ Y) clobbers(A,X,Y)	= $b3a2		; 8 bit unsigned Y -> float in fac1
asmsub	FREADSA		(byte value @ A) clobbers(A,X,Y)	= $bc3c		; 8 bit signed A -> float in fac1
asmsub	FREADSTR	(ubyte length @ A) clobbers(A,X,Y)	= $b7b5		; str -> fac1, $22/23 must point to string, A=string length
asmsub	FPRINTLN	() clobbers(A,X,Y)			= $aabc		; print string of fac1, on one line (= with newline) destroys fac1.  (consider FOUT + STROUT as well)
asmsub	FOUT		() clobbers(X) -> uword @ AY		= $bddd		; fac1 -> string, address returned in AY ($0100)

asmsub	FADDH		() clobbers(A,X,Y)			= $b849		; fac1 += 0.5, for rounding- call this before INT
asmsub	MUL10		() clobbers(A,X,Y)			= $bae2		; fac1 *= 10
asmsub	DIV10		() clobbers(A,X,Y)			= $bafe		; fac1 /= 10 , CAUTION: result is always positive!
asmsub	FCOMP		(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A = $bc5b		; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than

asmsub	FADDT		() clobbers(A,X,Y)			= $b86a		; fac1 += fac2
asmsub	FADD		(uword mflpt @ AY) clobbers(A,X,Y)	= $b867		; fac1 += mflpt value from A/Y
asmsub	FSUBT		() clobbers(A,X,Y)			= $b853		; fac1 = fac2-fac1   mind the order of the operands
asmsub	FSUB		(uword mflpt @ AY) clobbers(A,X,Y)	= $b850		; fac1 = mflpt from A/Y - fac1
asmsub	FMULTT 		() clobbers(A,X,Y)			= $ba2b		; fac1 *= fac2
asmsub	FMULT		(uword mflpt @ AY) clobbers(A,X,Y)	= $ba28		; fac1 *= mflpt value from A/Y
asmsub	FDIVT 		() clobbers(A,X,Y)			= $bb12		; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands
asmsub	FDIV  		(uword mflpt @ AY) clobbers(A,X,Y)	= $bb0f		; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
asmsub	FPWRT		() clobbers(A,X,Y)			= $bf7b		; fac1 = fac2 ** fac1
asmsub	FPWR		(uword mflpt @ AY) clobbers(A,X,Y)	= $bf78		; fac1 = fac2 ** mflpt from A/Y

asmsub	NOTOP		() clobbers(A,X,Y)			= $aed4		; fac1 = NOT(fac1)
asmsub	INT		() clobbers(A,X,Y)			= $bccc		; INT() truncates, use FADDH first to round instead of trunc
asmsub	LOG		() clobbers(A,X,Y)			= $b9ea		; fac1 = LN(fac1)  (natural log)
asmsub	SGN		() clobbers(A,X,Y)			= $bc39		; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
asmsub	SIGN		() -> ubyte @ A				= $bc2b		; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
asmsub	ABS		()					= $bc58		; fac1 = ABS(fac1)
asmsub	SQR		() clobbers(A,X,Y)			= $bf71		; fac1 = SQRT(fac1)
asmsub	SQRA		() clobbers(A,X,Y)			= $bf74		; fac1 = SQRT(fac2)
asmsub	EXP		() clobbers(A,X,Y)			= $bfed		; fac1 = EXP(fac1)  (e ** fac1)
asmsub	NEGOP		() clobbers(A)				= $bfb4		; switch the sign of fac1
asmsub	RND		() clobbers(A,X,Y)			= $e097		; fac1 = RND(fac1) float random number generator
asmsub	COS		() clobbers(A,X,Y)			= $e264		; fac1 = COS(fac1)
asmsub	SIN		() clobbers(A,X,Y)			= $e26b		; fac1 = SIN(fac1)
asmsub	TAN		() clobbers(A,X,Y)			= $e2b4		; fac1 = TAN(fac1)
asmsub	ATN		() clobbers(A,X,Y)			= $e30e		; fac1 = ATN(fac1)




asmsub  FREADS32  () clobbers(A,X,Y)  {
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

asmsub  FREADUS32  () clobbers(A,X,Y)  {
	; ---- fac1 = uint32 from $62-$65 big endian (MSB FIRST)
	%asm {{
		sec
		lda  #0
		ldx  #$a0
		jmp  $bc4f		; internal BASIC routine
	}}
}

asmsub  FREADS24AXY  (ubyte lo @ A, ubyte mid @ X, ubyte hi @ Y) clobbers(A,X,Y)  {
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

asmsub  GIVUAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	%asm {{
		sty  $62
		sta  $63
		ldx  #$90
		sec
		jmp  $bc49		; internal BASIC routine
	}}
}

asmsub  GIVAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- signed 16 bit word in A/Y (lo/hi) to float in fac1
	%asm {{
		sta  c64.SCRATCH_ZPREG
		tya
		ldy  c64.SCRATCH_ZPREG
		jmp  GIVAYF		; this uses the inverse order, Y/A
	}}
}

asmsub  FTOSWRDAY  () clobbers(X) -> uword @ AY  {
	; ---- fac1 to signed word in A/Y
	%asm {{
		jsr  FTOSWORDYA	; note the inverse Y/A order
		sta  c64.SCRATCH_ZPREG
		tya
		ldy  c64.SCRATCH_ZPREG
		rts
	}}
}

asmsub  GETADRAY  () clobbers(X) -> uword @ AY  {
	; ---- fac1 to unsigned word in A/Y
	%asm {{
		jsr  GETADR		; this uses the inverse order, Y/A
		sta  c64.SCRATCH_ZPB1
		tya
		ldy  c64.SCRATCH_ZPB1
		rts
	}}
}

sub  print_f  (float value) {
	; ---- prints the floating point value (without a newline) using basic rom routines.
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		lda  #<value
		ldy  #>value
		jsr  MOVFM		; load float into fac1
		jsr  FOUT		; fac1 to string in A/Y
		jsr  c64.STROUT			; print string in A/Y
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
}

sub  print_fln  (float value) {
	; ---- prints the floating point value (with a newline at the end) using basic rom routines
	%asm {{
		stx  c64.SCRATCH_ZPREGX
		lda  #<print_fln_value
		ldy  #>print_fln_value
		jsr  MOVFM		; load float into fac1
		jsr  FPRINTLN		; print fac1 with newline
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}

}

%asminclude "library:c64floats.asm", ""

}  ; ------ end of block c64flt
