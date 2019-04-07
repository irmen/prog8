; Prog8 definitions for floating point handling on the Commodore-64
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%option enable_floats


~ c64flt {
	; ---- this block contains C-64 floating point related functions ----
	
		const  float  PI	= 3.141592653589793
		const  float  TWOPI	= 6.283185307179586

	
; ---- C64 basic and kernal ROM float constants and functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

		; constants in five-byte "mflpt" format in the BASIC ROM
		memory  float  FL_PIVAL		= $aea8  ; 3.1415926...
		memory  float  FL_N32768	= $b1a5  ; -32768
		memory  float  FL_FONE		= $b9bc  ; 1
		memory  float  FL_SQRHLF	= $b9d6  ; SQR(2) / 2
		memory  float  FL_SQRTWO	= $b9db  ; SQR(2)
		memory  float  FL_NEGHLF	= $b9e0  ; -.5
		memory  float  FL_LOG2		= $b9e5  ; LOG(2)
		memory  float  FL_TENC		= $baf9  ; 10
		memory  float  FL_NZMIL		= $bdbd  ; 1e9 (1 billion)
		memory  float  FL_FHALF		= $bf11  ; .5
		memory  float  FL_LOGEB2	= $bfbf  ; 1 / LOG(2)
		memory  float  FL_PIHALF	= $e2e0  ; PI / 2
		memory  float  FL_TWOPI		= $e2e5  ; 2 * PI
		memory  float  FL_FR4		= $e2ea  ; .25


; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

; checked functions below:
asmsub	MOVFM		(uword mflpt @ AY) -> clobbers(A,Y) -> ()	= $bba2		; load mflpt value from memory  in A/Y into fac1
asmsub	FREADMEM	() -> clobbers(A,Y) -> ()			= $bba6		; load mflpt value from memory  in $22/$23 into fac1
asmsub	CONUPK		(uword mflpt @ AY) -> clobbers(A,Y) -> ()	= $ba8c		; load mflpt value from memory  in A/Y into fac2
asmsub	FAREADMEM	() -> clobbers(A,Y) -> ()			= $ba90		; load mflpt value from memory  in $22/$23 into fac2
asmsub	MOVFA		() -> clobbers(A,X) -> ()			= $bbfc		; copy fac2 to fac1
asmsub	MOVAF		() -> clobbers(A,X) -> ()			= $bc0c		; copy fac1 to fac2  (rounded)
asmsub	MOVEF		() -> clobbers(A,X) -> ()			= $bc0f		; copy fac1 to fac2
asmsub	MOVMF		(uword mflpt @ XY) -> clobbers(A,Y) -> ()	= $bbd4		; store fac1 to memory  X/Y as 5-byte mflpt

; fac1-> signed word in Y/A (might throw ILLEGAL QUANTITY)
; (tip: use c64flt.FTOSWRDAY to get A/Y output; lo/hi switched to normal little endian order)
asmsub	FTOSWORDYA	() -> clobbers(X) -> (ubyte @ Y, ubyte @ A)	= $b1aa

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use c64flt.GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
asmsub	GETADR		() -> clobbers(X) -> (ubyte @ Y, ubyte @ A)	= $b7f7

asmsub	QINT		() -> clobbers(A,X,Y) -> ()			= $bc9b		; fac1 -> 4-byte signed integer in 98-101 ($62-$65), with the MSB FIRST.
asmsub	AYINT		() -> clobbers(A,X,Y) -> ()			= $b1bf		; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; (tip: use c64flt.GIVAYFAY to use A/Y input; lo/hi switched to normal order)
; there is also c64flt.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; there is also c64flt.FREADS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADUS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADS24AXY  that reads signed int24 into fac1 from A/X/Y (lo/mid/hi bytes)
asmsub	GIVAYF		(ubyte lo @ Y, ubyte hi @ A) -> clobbers(A,X,Y) -> ()	= $b391

asmsub	FREADUY		(ubyte value @ Y) -> clobbers(A,X,Y) -> ()	= $b3a2		; 8 bit unsigned Y -> float in fac1
asmsub	FREADSA		(byte value @ A) -> clobbers(A,X,Y) -> ()	= $bc3c		; 8 bit signed A -> float in fac1
asmsub	FREADSTR	(ubyte length @ A) -> clobbers(A,X,Y) -> ()	= $b7b5		; str -> fac1, $22/23 must point to string, A=string length
asmsub	FPRINTLN	() -> clobbers(A,X,Y) -> ()			= $aabc		; print string of fac1, on one line (= with newline) destroys fac1.  (consider FOUT + STROUT as well)
asmsub	FOUT		() -> clobbers(X) -> (uword @ AY)		= $bddd		; fac1 -> string, address returned in AY ($0100)

asmsub	FADDH		() -> clobbers(A,X,Y) -> ()			= $b849		; fac1 += 0.5, for rounding- call this before INT
asmsub	MUL10		() -> clobbers(A,X,Y) -> ()			= $bae2		; fac1 *= 10
asmsub	DIV10		() -> clobbers(A,X,Y) -> ()			= $bafe		; fac1 /= 10 , CAUTION: result is always positive!
asmsub	FCOMP		(uword mflpt @ AY) -> clobbers(X,Y) -> (ubyte @ A) = $bc5b		; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than

asmsub	FADDT		() -> clobbers(A,X,Y) -> ()			= $b86a		; fac1 += fac2
asmsub	FADD		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $b867		; fac1 += mflpt value from A/Y
asmsub	FSUBT		() -> clobbers(A,X,Y) -> ()			= $b853		; fac1 = fac2-fac1   mind the order of the operands
asmsub	FSUB		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $b850		; fac1 = mflpt from A/Y - fac1
asmsub	FMULTT 		() -> clobbers(A,X,Y) -> ()			= $ba2b		; fac1 *= fac2
asmsub	FMULT		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $ba28		; fac1 *= mflpt value from A/Y
asmsub	FDIVT 		() -> clobbers(A,X,Y) -> ()			= $bb12		; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands
asmsub	FDIV  		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $bb0f		; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
asmsub	FPWRT		() -> clobbers(A,X,Y) -> ()			= $bf7b		; fac1 = fac2 ** fac1
asmsub	FPWR		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $bf78		; fac1 = fac2 ** mflpt from A/Y

asmsub	NOTOP		() -> clobbers(A,X,Y) -> ()			= $aed4		; fac1 = NOT(fac1)
asmsub	INT		() -> clobbers(A,X,Y) -> ()			= $bccc		; INT() truncates, use FADDH first to round instead of trunc
asmsub	LOG		() -> clobbers(A,X,Y) -> ()			= $b9ea		; fac1 = LN(fac1)  (natural log)
asmsub	SGN		() -> clobbers(A,X,Y) -> ()			= $bc39		; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
asmsub	SIGN		() -> clobbers() -> (ubyte @ A)			= $bc2b		; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
asmsub	ABS		() -> clobbers() -> ()				= $bc58		; fac1 = ABS(fac1)
asmsub	SQR		() -> clobbers(A,X,Y) -> ()			= $bf71		; fac1 = SQRT(fac1)
asmsub	SQRA		() -> clobbers(A,X,Y) -> ()			= $bf74		; fac1 = SQRT(fac2)
asmsub	EXP		() -> clobbers(A,X,Y) -> ()			= $bfed		; fac1 = EXP(fac1)  (e ** fac1)
asmsub	NEGOP		() -> clobbers(A) -> ()				= $bfb4		; switch the sign of fac1
asmsub	RND		() -> clobbers(A,X,Y) -> ()			= $e097		; fac1 = RND(fac1) float random number generator
asmsub	COS		() -> clobbers(A,X,Y) -> ()			= $e264		; fac1 = COS(fac1)
asmsub	SIN		() -> clobbers(A,X,Y) -> ()			= $e26b		; fac1 = SIN(fac1)
asmsub	TAN		() -> clobbers(A,X,Y) -> ()			= $e2b4		; fac1 = TAN(fac1)
asmsub	ATN		() -> clobbers(A,X,Y) -> ()			= $e30e		; fac1 = ATN(fac1)




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

asmsub  FREADS24AXY  (ubyte lo @ A, ubyte mid @ X, ubyte hi @ Y) -> clobbers(A,X,Y) -> ()  {
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

asmsub  GIVUAYFAY  (uword value @ AY) -> clobbers(A,X,Y) -> ()  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	%asm {{
		sty  $62
		sta  $63
		ldx  #$90
		sec
		jmp  $bc49		; internal BASIC routine
	}}
}

asmsub  GIVAYFAY  (uword value @ AY) -> clobbers(A,X,Y) -> ()  {
	; ---- signed 16 bit word in A/Y (lo/hi) to float in fac1
	%asm {{
		sta  c64.SCRATCH_ZPREG
		tya
		ldy  c64.SCRATCH_ZPREG
		jmp  c64flt.GIVAYF		; this uses the inverse order, Y/A
	}}
}

asmsub  FTOSWRDAY  () -> clobbers(X) -> (uword @ AY)  {
	; ---- fac1 to signed word in A/Y
	%asm {{
		jsr  c64flt.FTOSWORDYA	; note the inverse Y/A order
		sta  c64.SCRATCH_ZPREG
		tya
		ldy  c64.SCRATCH_ZPREG
		rts
	}}
}

asmsub  GETADRAY  () -> clobbers(X) -> (uword @ AY)  {
	; ---- fac1 to unsigned word in A/Y
	%asm {{
		jsr  c64flt.GETADR		; this uses the inverse order, Y/A
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
		lda  #<print_f_value
		ldy  #>print_f_value
		jsr  c64flt.MOVFM		; load float into fac1
		jsr  c64flt.FOUT		; fac1 to string in A/Y
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
		jsr  c64flt.MOVFM		; load float into fac1
		jsr  c64flt.FPRINTLN		; print fac1 with newline
		ldx  c64.SCRATCH_ZPREGX
		rts
	}}
        
}


; --- low level floating point assembly routines
%asm {{
ub2float	.proc
		; -- convert ubyte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		ldy  c64.SCRATCH_ZPB1
		jsr  c64flt.FREADUY
_fac_to_mem	ldx  c64.SCRATCH_ZPWORD2
		ldy  c64.SCRATCH_ZPWORD2+1
		jsr  c64flt.MOVMF
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend

b2float		.proc
		; -- convert byte in SCRATCH_ZPB1 to float at address A/Y
		;    clobbers A, Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		lda  c64.SCRATCH_ZPB1
		jsr  c64flt.FREADSA
		jmp  ub2float._fac_to_mem
		.pend

uw2float	.proc
		; -- convert uword in SCRATCH_ZPWORD1 to float at address A/Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64flt.GIVUAYFAY
		jmp  ub2float._fac_to_mem
		.pend

w2float		.proc
		; -- convert word in SCRATCH_ZPWORD1 to float at address A/Y
		stx  c64.SCRATCH_ZPREGX
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		ldy  c64.SCRATCH_ZPWORD1
		lda  c64.SCRATCH_ZPWORD1+1
		jsr  c64flt.GIVAYF
		jmp  ub2float._fac_to_mem
		.pend
		
stack_b2float	.proc
		; -- b2float operating on the stack
		inx
		lda  c64.ESTACK_LO,x
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.FREADSA
		jmp  push_fac1_as_result
		.pend
		
stack_w2float	.proc
		; -- w2float operating on the stack
		inx
		ldy  c64.ESTACK_LO,x
		lda  c64.ESTACK_HI,x
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.GIVAYF
		jmp  push_fac1_as_result
		.pend

stack_ub2float	.proc
		; -- ub2float operating on the stack
		inx
		lda  c64.ESTACK_LO,x
		stx  c64.SCRATCH_ZPREGX
		tay
		jsr  c64flt.FREADUY
		jmp  push_fac1_as_result
		.pend

stack_uw2float	.proc
		; -- uw2float operating on the stack
		inx
		lda  c64.ESTACK_LO,x
		ldy  c64.ESTACK_HI,x
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.GIVUAYFAY
		jmp  push_fac1_as_result
		.pend
		
stack_float2w	.proc	
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.AYINT
		ldx  c64.SCRATCH_ZPREGX
		lda  $64
		sta  c64.ESTACK_HI,x
		lda  $65
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend
		
stack_float2uw	.proc	
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.GETADR
		ldx  c64.SCRATCH_ZPREGX
		sta  c64.ESTACK_HI,x
		tya
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend

push_float	.proc
		; ---- push mflpt5 in A/Y onto stack 
		; (taking 3 stack positions = 6 bytes of which 1 is padding)
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #0
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_LO,x
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_HI,x
		dex
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_LO,x
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_HI,x
		dex
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  c64.ESTACK_LO,x
		dex
		rts
		.pend
		
func_rndf	.proc
		; -- put a random floating point value on the stack
		stx  c64.SCRATCH_ZPREG
		lda  #1
		jsr  c64flt.FREADSA
		jsr  c64flt.RND		; rng into fac1
		ldx  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jsr  c64flt.MOVMF	; fac1 to mem X/Y
		ldx  c64.SCRATCH_ZPREG
		lda  #<_rndf_rnum5
		ldy  #>_rndf_rnum5
		jmp  push_float
_rndf_rnum5	.byte  0,0,0,0,0
		.pend
		
push_float_from_indexed_var	.proc
		; -- push the float from the array at A/Y with index on stack, onto the stack.
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		jsr  prog8_lib.pop_index_times_5
		jsr  prog8_lib.add_a_to_zpword
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jmp  push_float
		.pend

pop_float	.proc
		; ---- pops mflpt5 from stack to memory A/Y
		; (frees 3 stack positions = 6 bytes of which 1 is padding)
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		ldy  #4
		inx
		lda  c64.ESTACK_LO,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		inx
		lda  c64.ESTACK_HI,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		lda  c64.ESTACK_LO,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		inx
		lda  c64.ESTACK_HI,x
		sta  (c64.SCRATCH_ZPWORD1),y
		dey
		lda  c64.ESTACK_LO,x
		sta  (c64.SCRATCH_ZPWORD1),y
		rts
		.pend
		
pop_float_fac1	.proc
		; -- pops float from stack into FAC1
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jmp  c64flt.MOVFM
		.pend
		
pop_float_to_indexed_var	.proc
		; -- pop the float on the stack, to the memory in the array at A/Y indexed by the byte on stack
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		jsr  prog8_lib.pop_index_times_5
		jsr  prog8_lib.add_a_to_zpword
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jmp  pop_float
		.pend

copy_float	.proc
		; -- copies the 5 bytes of the mflt value pointed to by SCRATCH_ZPWORD1, 
		;    into the 5 bytes pointed to by A/Y.  Clobbers A,Y.
		sta  c64.SCRATCH_ZPWORD2
		sty  c64.SCRATCH_ZPWORD2+1
		ldy  #0
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		sta  (c64.SCRATCH_ZPWORD2),y
		rts
		.pend

inc_var_f	.proc
		; -- add 1 to float pointed to by A/Y
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.MOVFM
		lda  #<FL_FONE
		ldy  #>FL_FONE
		jsr  c64flt.FADD
		ldx  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64flt.MOVMF
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend
                
dec_var_f	.proc
		; -- subtract 1 from float pointed to by A/Y
		sta  c64.SCRATCH_ZPWORD1
		sty  c64.SCRATCH_ZPWORD1+1
		stx  c64.SCRATCH_ZPREGX
		lda  #<FL_FONE
		ldy  #>FL_FONE
		jsr  c64flt.MOVFM
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64flt.FSUB
		ldx  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64flt.MOVMF
		ldx  c64.SCRATCH_ZPREGX
		rts
		.pend
		
inc_indexed_var_f	.proc
		; -- add 1 to float in array pointed to by A/Y, at index X
		pha
		txa
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1
		sta  c64.SCRATCH_ZPB1
		pla
		clc
		adc  c64.SCRATCH_ZPB1
		bcc  +
		iny
+		jmp  inc_var_f
		.pend
		
dec_indexed_var_f	.proc
		; -- subtract 1 to float in array pointed to by A/Y, at index X
		pha
		txa
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1
		sta  c64.SCRATCH_ZPB1
		pla
		clc
		adc  c64.SCRATCH_ZPB1
		bcc  +
		iny
+		jmp  dec_var_f
		.pend
		

pop_2_floats_f2_in_fac1	.proc
		; -- pop 2 floats from stack, load the second one in FAC1 as well
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jmp  c64flt.MOVFM
		.pend


fmath_float1	.byte 0,0,0,0,0	; storage for a mflpt5 value
fmath_float2	.byte 0,0,0,0,0	; storage for a mflpt5 value

push_fac1_as_result	.proc
		; -- push the float in FAC1 onto the stack, and return from calculation
		ldx  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.MOVMF
		lda  #<fmath_float1
		ldy  #>fmath_float1
		ldx  c64.SCRATCH_ZPREGX
		jmp  push_float
		.pend
		
pow_f		.proc
		; -- push f1 ** f2 on stack
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.CONUPK		; fac2 = float1
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  c64flt.FPWR
		ldx  c64.SCRATCH_ZPREGX
		jmp  push_fac1_as_result
		.pend
		
div_f		.proc
		; -- push f1/f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.FDIV
		jmp  push_fac1_as_result
		.pend

add_f		.proc
		; -- push f1+f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.FADD
		jmp  push_fac1_as_result
		.pend

sub_f		.proc
		; -- push f1-f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.FSUB
		jmp  push_fac1_as_result
		.pend

mul_f		.proc
		; -- push f1*f2 on stack
		jsr  pop_2_floats_f2_in_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.FMULT
		jmp  push_fac1_as_result
		.pend
		
neg_f		.proc
		; -- push -flt back on stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.NEGOP
		jmp  push_fac1_as_result
		.pend

abs_f		.proc
		; -- push abs(float) on stack (as float)
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.ABS
		jmp  push_fac1_as_result
		.pend

equal_f		.proc
		; -- are the two mflpt5 numbers on the stack identical?
		inx
		inx
		inx
		inx
		lda  c64.ESTACK_LO-3,x
		cmp  c64.ESTACK_LO,x
		bne  _equals_false
		lda  c64.ESTACK_LO-2,x
		cmp  c64.ESTACK_LO+1,x
		bne  _equals_false
		lda  c64.ESTACK_LO-1,x
		cmp  c64.ESTACK_LO+2,x
		bne  _equals_false
		lda  c64.ESTACK_HI-2,x
		cmp  c64.ESTACK_HI+1,x
		bne  _equals_false
		lda  c64.ESTACK_HI-1,x
		cmp  c64.ESTACK_HI+2,x
		bne  _equals_false
_equals_true	lda  #1
_equals_store	inx
		sta  c64.ESTACK_LO+1,x
		rts
_equals_false	lda  #0
		beq  _equals_store		
		.pend

notequal_f	.proc
		; -- are the two mflpt5 numbers on the stack different?
		jsr  equal_f
		eor  #1		; invert the result
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

less_f		.proc
		; -- is f1 < f2?
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend
		

lesseq_f	.proc
		; -- is f1 <= f2?
		jsr  compare_floats
		cmp  #255
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greater_f	.proc
		; -- is f1 > f2?
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

greatereq_f	.proc
		; -- is f1 >= f2?
		jsr  compare_floats
		cmp  #1
		beq  compare_floats._return_true
		cmp  #0
		beq  compare_floats._return_true
		bne  compare_floats._return_false
		.pend

compare_floats	.proc
		lda  #<fmath_float2
		ldy  #>fmath_float2
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  pop_float
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.MOVFM		; fac1 = flt1
		lda  #<fmath_float2
		ldy  #>fmath_float2
		stx  c64.SCRATCH_ZPREG
		jsr  c64flt.FCOMP		; A = flt1 compared with flt2 (0=equal, 1=flt1>flt2, 255=flt1<flt2)
		ldx  c64.SCRATCH_ZPREG
		rts
_return_false	lda  #0
_return_result  sta  c64.ESTACK_LO,x
		dex
		rts
_return_true	lda  #1
		bne  _return_result
		.pend		

func_sin	.proc
		; -- push sin(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.SIN
		jmp  push_fac1_as_result
		.pend

func_cos	.proc
		; -- push cos(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.COS
		jmp  push_fac1_as_result
		.pend

func_tan	.proc
		; -- push tan(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.TAN
		jmp  push_fac1_as_result
		.pend
		
func_atan	.proc
		; -- push atan(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.ATN
		jmp  push_fac1_as_result
		.pend
		
func_ln		.proc
		; -- push ln(f) back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.LOG
		jmp  push_fac1_as_result
		.pend
		
func_log2	.proc
		; -- push log base 2, ln(f)/ln(2), back onto stack
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.LOG
		jsr  c64flt.MOVEF
		lda  #<c64.FL_LOG2
		ldy  #>c64.FL_LOG2
		jsr  c64flt.MOVFM
		jsr  c64flt.FDIVT
		jmp  push_fac1_as_result
		.pend
		
func_sqrt	.proc
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.SQR
		jmp  push_fac1_as_result
		.pend
		
func_rad	.proc
		; -- convert degrees to radians (d * pi / 180)
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<_pi_div_180
		ldy  #>_pi_div_180
		jsr  c64flt.FMULT
		jmp  push_fac1_as_result
_pi_div_180	.byte 123, 14, 250, 53, 18		; pi / 180
		.pend
		
func_deg	.proc
		; -- convert radians to degrees (d * (1/ pi * 180))
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<_one_over_pi_div_180
		ldy  #>_one_over_pi_div_180
		jsr  c64flt.FMULT
		jmp  push_fac1_as_result
_one_over_pi_div_180	.byte 134, 101, 46, 224, 211		; 1 / (pi * 180)
		.pend
		
func_round	.proc
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.FADDH
		jsr  c64flt.INT
		jmp  push_fac1_as_result
		.pend
		
func_floor	.proc
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		jsr  c64flt.INT
		jmp  push_fac1_as_result
		.pend
		
func_ceil	.proc
		; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
		jsr  pop_float_fac1
		stx  c64.SCRATCH_ZPREGX
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.MOVMF
		jsr  c64flt.INT
		lda  #<fmath_float1
		ldy  #>fmath_float1
		jsr  c64flt.FCOMP
		cmp  #0
		beq  +
		lda  #<FL_FONE
		ldy  #>FL_FONE
		jsr  c64flt.FADD
+		jmp  push_fac1_as_result
		.pend

func_any_f	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1	; times 5 because of float
		jmp  func_any_b._entry
		.pend

func_all_f	.proc
		inx
		lda  c64.ESTACK_LO,x	; array size
		sta  c64.SCRATCH_ZPB1
		asl  a
		asl  a
		clc
		adc  c64.SCRATCH_ZPB1	; times 5 because of float
		sta  _cmp_mod+1		; self-modifying code
		jsr  peek_address
		ldy  #0
-		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		iny
		lda  (c64.SCRATCH_ZPWORD1),y
		bne  +
		lda  #0
		sta  c64.ESTACK_LO+1,x
		rts
+		iny
_cmp_mod	cpy  #255		; modified
		bne  -
		lda  #1
		sta  c64.ESTACK_LO+1,x
		rts
		.pend

func_max_f	.proc
		lda  #255
		sta  _minmax_cmp+1
		lda  #<_largest_neg_float
		ldy  #>_largest_neg_float
_minmax_entry	jsr  MOVFM
		jsr  prog8_lib.pop_array_and_lengthmin1Y
		stx  c64.SCRATCH_ZPREGX
-		sty  c64.SCRATCH_ZPREG
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  FCOMP
_minmax_cmp	cmp  #255			; modified
		bne  +
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  MOVFM
+		lda  c64.SCRATCH_ZPWORD1
		clc
		adc  #5
		sta  c64.SCRATCH_ZPWORD1
		bcc  +
		inc  c64.SCRATCH_ZPWORD1+1
+		ldy  c64.SCRATCH_ZPREG
		dey
		bne  -
		jmp  push_fac1_as_result
		rts
_largest_neg_float	.byte 255,255,255,255,255		; largest negative float -1.7014118345e+38		
		.pend

func_min_f	.proc
		lda  #1
		sta  func_max_f._minmax_cmp+1
		lda  #<_largest_pos_float
		ldy  #>_largest_pos_float
		jmp  func_max_f._minmax_entry
_largest_pos_float	.byte  255,127,255,255,255		; largest positive float 	
		rts
		.pend

func_sum_f	.proc
		lda  #<c64.FL_NEGHLF
		ldy  #>c64.FL_NEGHLF
		jsr  c64flt.MOVFM
		jsr  pop_array_and_lengthmin1Y
		stx  c64.SCRATCH_ZPREGX
-		sty  c64.SCRATCH_ZPREG
		lda  c64.SCRATCH_ZPWORD1
		ldy  c64.SCRATCH_ZPWORD1+1
		jsr  c64flt.FADD
		ldy  c64.SCRATCH_ZPREG
		dey
		cpy  #255
		beq  +
		lda  c64.SCRATCH_ZPWORD1
		clc
		adc  #5
		sta  c64.SCRATCH_ZPWORD1
		bcc  -
		inc  c64.SCRATCH_ZPWORD1+1
		bne  -
+		jsr  c64flt.FADDH
		jmp  push_fac1_as_result
		.pend
}}

}  ; ------ end of block c64flt
