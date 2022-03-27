; Prog8 definitions for floating point handling on the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%option enable_floats

floats {
	; ---- this block contains C-64 compatible floating point related functions ----
	;      the addresses are from cx16 V39 emulator and roms! they won't work on older versions.

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
romsub $fe30 = CONUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory  in A/Y into fac2
romsub $fe33 = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
romsub $fe36 = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive!
romsub $fe39 = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
romsub $fe3c = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands

romsub $fe42 = MOVFM(uword mflpt @ AY) clobbers(A,X,Y)      ; load mflpt value from memory  in A/Y into fac1
romsub $fe45 = MOVMF(uword mflpt @ XY) clobbers(A,X,Y)      ; store fac1 to memory  X/Y as 5-byte mflpt
romsub $fe48 = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
romsub $fe4b = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded)
romsub $fe4e = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2
romsub $fe54 = SIGN() clobbers(X,Y) -> ubyte @ A            ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
romsub $fe57 = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
romsub $fe5a = FREADSA(byte value @ A) clobbers(A,X,Y)      ; 8 bit signed A -> float in fac1
romsub $fe66 = ABS() clobbers(A,X,Y)                        ; fac1 = ABS(fac1)
romsub $fe69 = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than
romsub $fe72 = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to round instead of trunc
romsub $fe78 = FINLOG(byte value @A) clobbers (A, X, Y)           ; fac1 += signed byte in A
romsub $fe7b = FOUT() clobbers(X) -> uword @ AY             ; fac1 -> string, address returned in AY
romsub $fe81 = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)
romsub $fe84 = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1
romsub $fe8a = NEGOP() clobbers(A)                          ; switch the sign of fac1 (fac1 = -fac1)
romsub $fe8d = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
romsub $fe96 = RND() clobbers(A,X,Y)                        ; fac1 = RND(fac1) float random number generator
romsub $fe99 = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
romsub $fe9c = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
romsub $fe9f = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
romsub $fea2 = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)


asmsub  GIVUAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	%asm {{
        phx
        sta  _tmp
        sty  P8ZP_SCRATCH_B1
        tya
        ldy  _tmp
        jsr  GIVAYF                 ; load it as signed... correct afterwards
        lda  P8ZP_SCRATCH_B1
	    bpl  +
	    lda  #<_flt65536
	    ldy  #>_flt65536
	    jsr  FADD
+	    plx
        rts
_tmp        .byte 0
_flt65536    .byte 145,0,0,0,0       ; 65536.0
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
        lda  #0
        jmp  GIVAYF
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

sub pow(float value, float power) -> float {
    %asm {{
        phx
        phy
        lda  #<value
        ldy  #>value
        jsr  floats.CONUPK
        lda  #<power
        ldy  #>power
        jsr  floats.MOVFM
        jsr  floats.FPWRT       ; cx16 doesn't have FPWR
        ply
        plx
        rts
    }}
}

%asminclude "library:c64/floats.asm"
%asminclude "library:c64/floats_funcs.asm"

}
