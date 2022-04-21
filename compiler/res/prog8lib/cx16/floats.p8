; Prog8 definitions for floating point handling on the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%option enable_floats
%import floats_functions

floats {
	; ---- this block contains C-64 compatible floating point related functions ----
	;      the addresses are from cx16 V39 emulator and roms! they won't work on older versions.

        const float  PI     = 3.141592653589793
        const float  TWOPI  = 6.283185307179586


; ---- ROM float functions (same as on C128 except base page) ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

romsub $fe00 = AYINT() clobbers(A,X,Y)          ; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; there is also floats.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; (tip: use GIVAYFAY to use A/Y input; lo/hi switched to normal order)
romsub $fe03 = GIVAYF(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)

romsub $fe06 = FOUT() clobbers(X) -> uword @ AY             ; fac1 -> string, address returned in AY
; romsub $fe09 = VAL_1() clobbers(A,X,Y)                      ; convert ASCII string to floating point [not yet implemented!!!]

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
romsub $fe0c = GETADR() clobbers(X) -> ubyte @ Y, ubyte @ A
romsub $fe0f = FLOATC() clobbers(A,X,Y)                     ; convert address to floating point

romsub $fe12 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1
romsub $fe15 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2-fac1   mind the order of the operands
romsub $fe18 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt value from A/Y
romsub $fe1b = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
romsub $fe1e = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt value from A/Y
romsub $fe21 = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
romsub $fe24 = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
romsub $fe27 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands
romsub $fe2a = LOG() clobbers(A,X,Y)                        ; fac1 = LN(fac1)  (natural log)
romsub $fe2d = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to round instead of trunc
romsub $fe30 = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)
romsub $fe33 = NEGOP() clobbers(A)                          ; switch the sign of fac1 (fac1 = -fac1)
romsub $fe36 = FPWR(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = fac2 ** float in A/Y
romsub $fe39 = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1
romsub $fe3c = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
romsub $fe3f = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
romsub $fe42 = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
romsub $fe45 = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
romsub $fe48 = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)
romsub $fe4b = ROUND() clobbers(A,X,Y)                      ; round fac1
romsub $fe4e = ABS() clobbers(A,X,Y)                        ; fac1 = ABS(fac1)
romsub $fe51 = SIGN() clobbers(X,Y) -> ubyte @ A            ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
romsub $fe54 = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than
romsub $fe57 = RND_0() clobbers(A,X,Y)                      ; fac1 = RND(fac1) float random number generator  NOTE: special cx16 setup required, use RND() stub instead!!
romsub $fe5a = CONUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in A/Y into fac2
romsub $fe5d = ROMUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in current bank in A/Y into fac2
romsub $fe60 = MOVFRM(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in A/Y into fac1  (use MOVFM instead)
romsub $fe63 = MOVFM(uword mflpt @ AY) clobbers(A,X,Y)      ; load mflpt value from memory in A/Y into fac1
romsub $fe66 = MOVMF(uword mflpt @ XY) clobbers(A,X,Y)      ; store fac1 to memory  X/Y as 5-byte mflpt
romsub $fe69 = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
romsub $fe6c = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded)

; X16 additions
romsub $fe81 = FADDH() clobbers(A,X,Y)                      ; fac1 += 0.5, for rounding- call this before INT
romsub $fe84 = ZEROFC() clobbers(A,X,Y)                     ; fac1 = 0
romsub $fe87 = NORMAL() clobbers(A,X,Y)                     ; normalize fac1 (?)
romsub $fe8a = NEGFAC() clobbers(A)                         ; switch the sign of fac1 (fac1 = -fac1) (juse use NEGOP() instead!)
romsub $fe8d = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
romsub $fe90 = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive!
romsub $fe93 = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2
romsub $fe96 = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
romsub $fe99 = FLOAT() clobbers(A,X,Y)                      ; FAC = (u8).A
romsub $fe9c = FLOATS() clobbers(A,X,Y)                     ; FAC = (s16)facho+1:facho
romsub $fe9f = QINT() clobbers(A,X,Y)                       ; facho:facho+1:facho+2:facho+3 = u32(FAC)
romsub $fea2 = FINLOG(byte value @A) clobbers (A, X, Y)     ; fac1 += signed byte in A


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
	sty  $c4        ; facmo     ($64 on c128)
	sta  $c5        ; facmo+1   ($65 on c128)
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

asmsub  RND() clobbers(A,X,Y) {
    %asm {{
        lda  #0
        php
        jsr  cx16.entropy_get
        plp
        jmp  RND_0
    }}
}


%asminclude "library:c64/floats.asm"
%asminclude "library:c64/floats_funcs.asm"

}
