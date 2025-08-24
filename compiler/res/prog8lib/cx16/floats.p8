; Prog8 definitions for floating point handling on the CommanderX16

%option enable_floats, no_symbol_prefixing, ignore_unused
%import shared_floats_functions

floats {
; ---- this block contains C-64 compatible floating point related functions ----

; ---- ROM float functions (same as on C128 except base page) ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

extsub $fe00 = AYINT() clobbers(A,X,Y)          ; fac1-> signed word in 'facmo' and 'faclo', MSB FIRST. DON'T USE THIS, USE WRAPPER 'AYINT2' INSTEAD.  (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; there is also floats.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; (tip: use GIVAYFAY to use A/Y input; lo/hi switched to normal order)
extsub $fe03 = GIVAYF(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)

extsub $fe06 = FOUT() clobbers(X) -> str @ AY             ; fac1 -> string, address returned in AY
extsub $fe09 = VAL_1(str string @XY, ubyte length @A) clobbers(A,X,Y) -> float @FAC1      ; convert ASCII string in XY and length in A, to floating point in FAC1. WARNING: only implemented in ROM 47+. Safer to use floats.parse() instead.

; GETADR: fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
extsub $fe0c = GETADR() clobbers(X) -> ubyte @ Y, ubyte @ A
extsub $fe0f = FLOATC() clobbers(A,X,Y)                     ; convert address to floating point

extsub $fe12 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1
extsub $fe15 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2-fac1   mind the order of the operands
extsub $fe18 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt value from A/Y
extsub $fe1b = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
extsub $fe1e = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt value from A/Y
extsub $fe21 = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
extsub $fe24 = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1
extsub $fe27 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  mind the order of the operands
extsub $fe2a = LOG() clobbers(A,X,Y)                        ; fac1 = LN(fac1)  (natural log)
extsub $fe2d = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to integer round instead of trunc
extsub $fe30 = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)
extsub $fe33 = NEGOP() clobbers(A)                          ; switch the sign of fac1 (fac1 = -fac1)
extsub $fe36 = FPWR(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = fac2 ** float in A/Y
extsub $fe39 = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1
extsub $fe3c = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
extsub $fe3f = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
extsub $fe42 = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
extsub $fe45 = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
extsub $fe48 = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)
extsub $fe4b = ROUND() clobbers(A,X,Y)                      ; round least significant bit of fac1
extsub $fe4e = ABS() clobbers(A,X,Y)                        ; fac1 = ABS(fac1)
extsub $fe51 = SIGN() clobbers(X,Y) -> ubyte @ A            ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
extsub $fe54 = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than
extsub $fe57 = RND_0() clobbers(A,X,Y)                      ; fac1 = RND(fac1) float random number generator  NOTE: incompatible with C64's RND routine
extsub $fe57 = RND() clobbers(A,X,Y)                        ; alias for RND_0
extsub $fe5a = CONUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in A/Y into fac2
extsub $fe5d = ROMUPK(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in current bank in A/Y into fac2
extsub $fe60 = MOVFRM(uword mflpt @ AY) clobbers(A,X,Y)     ; load mflpt value from memory in A/Y into fac1  (use MOVFM instead)
extsub $fe63 = MOVFM(uword mflpt @ AY) clobbers(A,X,Y)      ; load mflpt value from memory in A/Y into fac1
extsub $fe66 = MOVMF(uword mflpt @ XY) clobbers(A,X,Y)      ; store fac1 to memory  X/Y as 5-byte mflpt
extsub $fe69 = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
extsub $fe6c = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded the least significant bit)

; X16 additions
extsub $fe6f = FADDH() clobbers(A,X,Y)                      ; fac1 += 0.5, for integer rounding- call this before INT
extsub $fe72 = ZEROFC() clobbers(A,X,Y)                     ; fac1 = 0
extsub $fe75 = NORMAL() clobbers(A,X,Y)                     ; normalize fac1
extsub $fe78 = NEGFAC() clobbers(A)                         ; switch the sign of fac1 (fac1 = -fac1) (doesn't work, juse use NEGOP() instead!)
extsub $fe7b = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
extsub $fe7e = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive! Have to restore sign manually!
extsub $fe81 = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2
extsub $fe84 = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
extsub $fe87 = FLOAT() clobbers(A,X,Y)                      ; FAC = (s8).A
extsub $fe8a = FLOATS() clobbers(A,X,Y)                     ; FAC = (s16)facho+1:facho
extsub $fe8d = QINT() clobbers(A,X,Y)                       ; facho:facho+1:facho+2:facho+3 = u32(FAC)
extsub $fe90 = FINLOG(byte value @A) clobbers (A, X, Y)     ; fac1 += signed byte in A


asmsub  AYINT2() clobbers(X) -> word @AY {
    ; fac1-> signed word in AY. Safe wrapper around the AYINT kernal routine (not reading internal memory locations)
    ; (might throw ILLEGAL QUANTITY)
    %asm {{
		jsr  AYINT
		ldx  #<floats_temp_var
		ldy  #>floats_temp_var
		jsr  MOVMF
		lda  floats_temp_var+4
		ldy  floats_temp_var+3
        rts
    }}
}

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

asmsub FREADU24AXY(ubyte lo @ A, ubyte mid @ X, ubyte hi @ Y) clobbers(A, X, Y) -> float @FAC1 {
    ; ---- fac1 = unsigned int24 (A/X/Y contain lo/mid/hi bytes)
        %asm{{
                 FAC = $C3
                 sty FAC+1
                 stx FAC+2
                 sta FAC+3

                 cpy #$00
                 bne +
                 cpx #$00
                 bne +
                 cmp #$00
                 beq ++

              +  ldx #$98
                 bit FAC+1
                 bmi +

              -  dex
                 asl FAC+3
                 rol FAC+2
                 rol FAC+1
                 bpl -

               + stx FAC
                 stz FAC+4
                 stz FAC+5
                 rts
       }}
}

asmsub  GIVUAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- unsigned 16 bit word in A/Y (lo/hi) to fac1
	; See "basic.sym" kernal symbol file for the facmo memory location.
	; TODO find a way to not depend on that internal fac memory location. MOVFM doesn't work.
	%asm {{
	sty  $c4        ; facmo
	sta  $c5        ; facmo+1
	ldx  #$90
	sec
	jmp  FLOATC
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

asmsub parse(str value @AY) -> float @FAC1 {
    ; -- Parse a string value of a number to float in FAC1.
    ;    Requires kernal R47 or newer! (depends on val_1 working)
    %asm {{
        ldx  VAL_1
        cpx  #$4c       ; is there an implementation in VAL_1? (test for JMP)
        bne  _borked    ; no, print error message
        pha             ; yes, count the length and call rom VAL_1.
        phy
        jsr  prog8_lib.strlen
        tya
        ply
        plx
        jmp  VAL_1
_borked
        ldy  #0
-       lda  _msg,y
        beq  +
        jsr  cbm.CHROUT
        iny
        bne  -
+       jmp  sys.exit

_msg    .text 13,"?rom 47+ required for val1",13,0
        ; !notreached!
    }}
}


sub rnd() -> float {
    %asm {{
        lda  #1
        jmp  RND_0
    }}
}

asmsub normalize(float value @FAC1) -> float @ FAC1 {
    %asm {{
        jmp  floats.NORMAL
    }}
}

; get the jiffy clock as a float
asmsub time() -> float @ FAC1 {
    %asm {{
        jsr cbm.RDTIM
        jmp floats.FREADU24AXY
    }}
}

%asminclude "library:c64/floats.asm"
%asminclude "library:c64/floats_funcs.asm"


}
