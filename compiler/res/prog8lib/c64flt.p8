; Prog8 definitions for floating point handling on the Commodore-64
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target c64
%option enable_floats

c64flt {
	; ---- this block contains C-64 floating point related functions ----

        const float  PI     = 3.141592653589793
        const float  TWOPI  = 6.283185307179586


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
		; oddly enough, 0.0 isn't available in the kernel.


; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

romsub $bba2 = MOVFM(uword mflpt @ AY) clobbers(A,Y)        ; load mflpt value from memory  in A/Y into fac1
romsub $bba6 = FREADMEM() clobbers(A,Y)                     ; load mflpt value from memory  in $22/$23 into fac1
romsub $ba8c = CONUPK(uword mflpt @ AY) clobbers(A,Y)       ; load mflpt value from memory  in A/Y into fac2
romsub $ba90 = FAREADMEM() clobbers(A,Y)                    ; load mflpt value from memory  in $22/$23 into fac2
romsub $bbfc = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
romsub $bc0c = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded)
romsub $bc0f = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2
romsub $bbd4 = MOVMF(uword mflpt @ XY) clobbers(A,Y)        ; store fac1 to memory  X/Y as 5-byte mflpt

; fac1-> signed word in Y/A (might throw ILLEGAL QUANTITY)
; (tip: use c64flt.FTOSWRDAY to get A/Y output; lo/hi switched to normal little endian order)
romsub $b1aa = FTOSWORDYA() clobbers(X) -> ubyte @ Y, ubyte @ A       ; note: calls AYINT.

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use c64flt.GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
romsub $b7f7 = GETADR() clobbers(X) -> ubyte @ Y, ubyte @ A

romsub $bc9b = QINT() clobbers(A,X,Y)           ; fac1 -> 4-byte signed integer in 98-101 ($62-$65), with the MSB FIRST.
romsub $b1bf = AYINT() clobbers(A,X,Y)          ; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; (tip: use c64flt.GIVAYFAY to use A/Y input; lo/hi switched to normal order)
; there is also c64flt.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; there is also c64flt.FREADS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADUS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADS24AXY  that reads signed int24 into fac1 from A/X/Y (lo/mid/hi bytes)
romsub $b391 = GIVAYF(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)

romsub $b3a2 = FREADUY(ubyte value @ Y) clobbers(A,X,Y)     ; 8 bit unsigned Y -> float in fac1
romsub $bc3c = FREADSA(byte value @ A) clobbers(A,X,Y)      ; 8 bit signed A -> float in fac1
romsub $b7b5 = FREADSTR(ubyte length @ A) clobbers(A,X,Y)   ; str -> fac1, $22/23 must point to string, A=string length
romsub $aabc = FPRINTLN() clobbers(A,X,Y)                   ; print string of fac1, on one line (= with newline) destroys fac1.  (consider FOUT + STROUT as well)
romsub $bddd = FOUT() clobbers(X) -> uword @ AY             ; fac1 -> string, address returned in AY ($0100)

romsub $b849 = FADDH() clobbers(A,X,Y)                      ; fac1 += 0.5, for rounding- call this before INT
romsub $bae2 = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
romsub $bafe = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive!
romsub $bc5b = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than

romsub $b86a = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
romsub $b867 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt value from A/Y
romsub $b853 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2-fac1   mind the order of the operands
romsub $b850 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1
romsub $ba2b = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
romsub $ba28 = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt value from A/Y
romsub $bb12 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands
romsub $bb0f = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
romsub $bf7b = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1
romsub $bf78 = FPWR(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = fac2 ** mflpt from A/Y
romsub $bd7e = FINLOG(byte value @A) clobbers (A, X, Y)     ; fac1 += signed byte in A

romsub $aed4 = NOTOP() clobbers(A,X,Y)                      ; fac1 = NOT(fac1)
romsub $bccc = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to round instead of trunc
romsub $b9ea = LOG() clobbers(A,X,Y)                        ; fac1 = LN(fac1)  (natural log)
romsub $bc39 = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
romsub $bc2b = SIGN() -> ubyte @ A                          ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
romsub $bc58 = ABS()                                        ; fac1 = ABS(fac1)
romsub $bf71 = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)
romsub $bf74 = SQRA() clobbers(A,X,Y)                       ; fac1 = SQRT(fac2)
romsub $bfed = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
romsub $bfb4 = NEGOP() clobbers(A)                          ; switch the sign of fac1
romsub $e097 = RND() clobbers(A,X,Y)                        ; fac1 = RND(fac1) float random number generator
romsub $e264 = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
romsub $e26b = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
romsub $e2b4 = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
romsub $e30e = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)



asmsub  FREADS32() clobbers(A,X,Y)  {
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
		sta  P8ZP_SCRATCH_REG
		tya
		ldy  P8ZP_SCRATCH_REG
		jmp  GIVAYF		; this uses the inverse order, Y/A
	}}
}

asmsub  FTOSWRDAY  () clobbers(X) -> uword @ AY  {
	; ---- fac1 to signed word in A/Y
	%asm {{
		jsr  FTOSWORDYA	; note the inverse Y/A order
		sta  P8ZP_SCRATCH_REG
		tya
		ldy  P8ZP_SCRATCH_REG
		rts
	}}
}

asmsub  GETADRAY  () clobbers(X) -> uword @ AY  {
	; ---- fac1 to unsigned word in A/Y
	%asm {{
		jsr  GETADR		; this uses the inverse order, Y/A
		sta  P8ZP_SCRATCH_B1
		tya
		ldy  P8ZP_SCRATCH_B1
		rts
	}}
}

sub  print_f  (float value) {
	; ---- prints the floating point value (without a newline).
	%asm {{
		stx  P8ZP_SCRATCH_REG_X
		lda  #<value
		ldy  #>value
		jsr  MOVFM		; load float into fac1
		jsr  FOUT		; fac1 to string in A/Y
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  (P8ZP_SCRATCH_B1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
		ldx  P8ZP_SCRATCH_REG_X
+		rts
	}}
}

%asminclude "library:c64floats.asm", ""

}
