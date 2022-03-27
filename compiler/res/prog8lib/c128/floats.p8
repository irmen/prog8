; Prog8 definitions for floating point handling on the Commodore 128
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%option enable_floats

floats {
	; ---- this block contains C-128 compatible floating point related functions ----

        const float  PI     = 3.141592653589793
        const float  TWOPI  = 6.283185307179586


; ---- ROM float functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

romsub $af00 = AYINT() clobbers(A,X,Y)          ; fac1-> signed word in 102-103 ($66-$67) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; there is also floats.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; (tip: use GIVAYFAY to use A/Y input; lo/hi switched to normal order)
romsub $af03 = GIVAYF(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)

romsub $af06 = FOUT() clobbers(X) -> uword @ AY             ; fac1 -> string, address returned in AY
; romsub $af09 = VAL_1() clobbers(A,X,Y)                      ; convert ASCII string to floating point [not yet implemented!!!]

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
romsub $af0c = GETADR() clobbers(X) -> ubyte @ Y, ubyte @ A
romsub $af0f = FLOATC() clobbers(A,X,Y)                     ; convert address to floating point

romsub $af12 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1
romsub $af15 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2-fac1   mind the order of the operands         NOTE: use FSUBT2() instead!
romsub $af18 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt value from A/Y
romsub $af1b = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2      NOTE: use FADDT2() instead!
romsub $af1e = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt value from A/Y
romsub $af21 = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2      NOTE: use FMULTT2() instead!
romsub $af24 = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
romsub $af27 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands  NOTE: use FDIVT2() instead!
romsub $af2a = LOG() clobbers(A,X,Y)                        ; fac1 = LN(fac1)  (natural log)
romsub $af2d = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to round instead of trunc
romsub $af30 = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)
romsub $af33 = NEGOP() clobbers(A)                          ; switch the sign of fac1 (fac1 = -fac1)
romsub $af36 = FPWR(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = fac2 ** float in A/Y
romsub $af39 = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1           NOTE: use FPWRT2() instead!
romsub $af3c = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
romsub $af3f = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
romsub $af42 = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
romsub $af45 = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
romsub $af48 = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)
romsub $af4b = ROUND() clobbers(A,X,Y)                      ; round fac1
romsub $af4e = ABS() clobbers(A,X,Y)                        ; fac1 = ABS(fac1)
romsub $af51 = SIGN() clobbers(X,Y) -> ubyte @ A            ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
romsub $af54 = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than
romsub $af57 = RND_0() clobbers(A,X,Y)                      ; fac1 = RND(fac1) float random number generator  NOTE: special cx16 setup required, use RND() stub instead!!
romsub $af5a = CONUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory  in A/Y into fac2
romsub $af5d = ROMUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in current bank in A/Y into fac2
romsub $af60 = MOVFRM(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in A/Y into fac1  (use MOVFM instead)
romsub $af63 = MOVFM(uword mflpt @ AY) clobbers(A,X,Y)      ; load mflpt value from memory  in A/Y into fac1
romsub $af66 = MOVMF(uword mflpt @ XY) clobbers(A,X,Y)      ; store fac1 to memory  X/Y as 5-byte mflpt
romsub $af69 = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
romsub $af6c = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded)

; X16 additions
romsub $af6f = FADDH() clobbers(A,X,Y)                      ; fac1 += 0.5, for rounding- call this before INT
romsub $af72 = FADDT2() clobbers(A,X,Y)                     ; fac1 += fac2
romsub $af75 = ZEROFC() clobbers(A,X,Y)                     ; fac1 = 0
romsub $af78 = NORMAL() clobbers(A,X,Y)                     ; normalize fac1 (?)
romsub $af7b = NEGFAC() clobbers(A)                         ; switch the sign of fac1 (fac1 = -fac1) (juse use NEGOP() instead!)
romsub $af7e = FMULTT2() clobbers(A,X,Y)                    ; fac1 *= fac2
romsub $af81 = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
romsub $af84 = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive!
romsub $af87 = FDIVT2() clobbers(A,X,Y)                     ; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands
romsub $af8a = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2
romsub $af8d = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
romsub $af90 = FLOAT() clobbers(A,X,Y)                      ; FAC = (u8).A
romsub $af93 = FLOATS() clobbers(A,X,Y)                     ; FAC = (s16)facho+1:facho
romsub $af9C = QINT() clobbers(A,X,Y)                       ; facho:facho+1:facho+2:facho+3 = u32(FAC)
romsub $af9f = FINLOG(byte value @A) clobbers (A, X, Y)     ; fac1 += signed byte in A
romsub $afa5 = FPWRT2() clobbers(A,X,Y)                     ; fac1 = fac2 ** fac1

asmsub  FREADSA  (byte value @A) clobbers(A,X,Y) {
    ; ---- 8 bit signed A -> float in fac1
    %asm {{
        tay
        bpl  +
        lda  #$ff
        jmp  GIVAYF
+       lda  #0
        jmp  GIVAYF
    }}
}

asmsub  GIVUAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	%asm {{
	phx
	sty  $64        ; facmo
	sta  $65        ; facmo+1
	ldx  #$90
	sec
	jsr  FLOATC
	plx
	rts
	}}
}


asmsub  GIVAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- signed 16 bit word in A/Y (lo/hi) to float in fac1
	%asm {{
		sta  P8ZP_SCRATCH_B1
		tya
		ldy  P8ZP_SCRATCH_B1
		jmp  GIVAYF		; this uses the inverse order, Y/A
	}}
}

asmsub  FTOSWRDAY  () clobbers(X) -> uword @ AY  {
	; ---- fac1 to signed word in A/Y
	%asm {{
		jsr  FTOSWORDYA	; note the inverse Y/A order
		sta  P8ZP_SCRATCH_B1
		tya
		ldy  P8ZP_SCRATCH_B1
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

asmsub  FREADUY (ubyte value @Y) {
    ; -- 8 bit unsigned Y -> float in fac1
    %asm {{
        tya
        jmp  FLOAT
    }}
}

sub  print_f  (float value) {
	; ---- prints the floating point value (without a newline).
	%asm {{
		stx  P8ZP_SCRATCH_REG
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
+		ldx  P8ZP_SCRATCH_REG
		rts
	}}
}

sub pow(float value, float power) -> float {
    %asm {{
        phx
        phy
        lda  #<value
        ldy  #>value
        jsr  floats.CONUPK
        lda  #<power
        ldy  #>power
        jsr  floats.FPWR
        ply
        plx
        rts
    }}
}


%asminclude "library:c128/floats.asm"
%asminclude "library:c64/floats_funcs.asm"

}
