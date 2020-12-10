; Prog8 definitions for floating point handling on the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target cx16
%option enable_floats

floats {
	; ---- this block contains C-64 floating point related functions ----

        const float  PI     = 3.141592653589793
        const float  TWOPI  = 6.283185307179586


; ---- ROM float functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

romsub $fe00 = AYINT() clobbers(A,X,Y)          ; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; there is also floats.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; (tip: use GIVAYFAY to use A/Y input; lo/hi switched to normal order)
romsub $fe03 = GIVAYF(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
romsub $fe06 = GETADR() clobbers(X) -> ubyte @ Y, ubyte @ A

romsub $fe09 = FADDH() clobbers(A,X,Y)                      ; fac1 += 0.5, for rounding- call this before INT
romsub $fe0c = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1
romsub $fe0f = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2-fac1   mind the order of the operands
romsub $fe12 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt value from A/Y
romsub $fe15 = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
romsub $fe1b = ZEROFC() clobbers(A,X,Y)                     ; fac1 = 0
romsub $fe1e = NORMAL() clobbers(A,X,Y)                     ; normalize fac1 (?)
romsub $fe24 = LOG() clobbers(A,X,Y)                        ; fac1 = LN(fac1)  (natural log)
romsub $fe27 = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt value from A/Y
romsub $fe2a = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
romsub $fe33 = CONUPK(uword mflpt @ AY) clobbers(A,Y)       ; load mflpt value from memory  in A/Y into fac2
romsub $fe36 = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
romsub $fe3c = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive!
romsub $fe3f = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
romsub $fe42 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands

romsub $fe48 = MOVFM(uword mflpt @ AY) clobbers(A,Y)        ; load mflpt value from memory  in A/Y into fac1
romsub $fe4b = MOVMF(uword mflpt @ XY) clobbers(A,Y)        ; store fac1 to memory  X/Y as 5-byte mflpt
romsub $fe4e = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
romsub $fe51 = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded)
romsub $fe54 = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2
romsub $fe5a = SIGN() -> ubyte @ A                          ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
romsub $fe5d = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
romsub $fe60 = FREADSA(byte value @ A) clobbers(A,X,Y)      ; 8 bit signed A -> float in fac1
romsub $fe6c = ABS()                                        ; fac1 = ABS(fac1)
romsub $fe6f = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than
romsub $fe78 = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to round instead of trunc
romsub $fe7e = FINLOG(byte value @A) clobbers (A, X, Y)           ; fac1 += signed byte in A
romsub $fe81 = FOUT() clobbers(X) -> uword @ AY             ; fac1 -> string, address returned in AY
romsub $fe8a = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)
romsub $fe8d = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1
; note: there is no FPWR() on the Cx16
romsub $fe93 = NEGOP() clobbers(A)                          ; switch the sign of fac1
romsub $fe96 = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
romsub $fe9f = RND2(byte value @A) clobbers(A,X,Y)                ; fac1 = RND(A) float random number generator
romsub $fea2 = RND() clobbers(A,X,Y)                        ; fac1 = RND(fac1) float random number generator
romsub $fea5 = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
romsub $fea8 = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
romsub $feab = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
romsub $feae = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)


asmsub  GIVUAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	%asm {{
        phx
        sta  P8ZP_SCRATCH_W2
        sty  P8ZP_SCRATCH_B1
        tya
        ldy  P8ZP_SCRATCH_W2
        jsr  GIVAYF                 ; load it as signed... correct afterwards
        lda  P8ZP_SCRATCH_B1
	    bpl  +
	    lda  #<_flt65536
	    ldy  #>_flt65536
	    jsr  FADD
+	    plx
        rts
_flt65536    .byte 145,0,0,0,0       ; 65536.0
	}}
}


asmsub  GIVAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- signed 16 bit word in A/Y (lo/hi) to float in fac1
	%asm {{
		sta  P8ZP_SCRATCH_W2
		tya
		ldy  P8ZP_SCRATCH_W2
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
		phx
		lda  #<value
		ldy  #>value
		jsr  MOVFM		; load float into fac1
		jsr  FOUT		; fac1 to string in A/Y
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		plx
		rts
	}}
}

%asminclude "library:c64/floats.asm", ""
%asminclude "library:c64/floats_funcs.asm", ""

}
