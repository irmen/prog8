%option enable_floats, no_symbol_prefixing, ignore_unused
%import shared_floats_functions

floats {

; ---- this block contains C-64 floating point related functions ----

; ---- C64 basic and kernal ROM float constants and functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

const uword FAC_ADDR = $5e

; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

;extsub $bba6 = FREADMEM() clobbers(A,Y)                     ; load mflpt value from memory  in $22/$23 into fac1
;extsub $ba90 = FAREADMEM() clobbers(A,Y)                    ; load mflpt value from memory  in $22/$23 into fac2
;;
;
;extsub $b3a2 = FREADUY(ubyte value @ Y) clobbers(A,X,Y)     ; 8 bit unsigned Y -> float in fac1
;extsub $bc3c = FREADSA(byte value @ A) clobbers(A,X,Y)      ; 8 bit signed A -> float in fac1
;extsub $b7b5 = FREADSTR(ubyte length @ A) clobbers(A,X,Y)   ; str -> fac1, $22/23 must point to string, A=string length.  Also see parse()
;extsub $aabc = FPRINTLN() clobbers(A,X,Y)                   ; print string of fac1, on one line (= with newline) destroys fac1.  (consider FOUT + STROUT as well)
;
;extsub $bc5b = FCOMP(uword mflpt @ AY) clobbers(X,Y) -> ubyte @ A   ; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than
;
;extsub $bf7b = FPWRT() clobbers(A,X,Y)                      ; fac1 = fac2 ** fac1
;extsub $bf78 = FPWR(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = fac2 ** mflpt from A/Y
;extsub $bd7e = FINLOG(byte value @A) clobbers (A, X, Y)     ; fac1 += signed byte in A
;
;extsub $aed4 = NOTOP() clobbers(A,X,Y)                      ; fac1 = NOT(fac1)
;extsub $bc39 = SGN() clobbers(A,X,Y)                        ; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
;extsub $bf74 = SQRA() clobbers(A,X,Y)                       ; fac1 = SQRT(fac2)


; PET32 BASIC 4.0 ADDRESSES FOUND:

;; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
;; (tip: use floats.GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
extsub $c92d = GETADR() clobbers(X) -> ubyte @ Y, ubyte @ A

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; (tip: use floats.GIVAYFAY to use A/Y input; lo/hi switched to normal order)
; there is also floats.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
extsub $c4bc = GIVAYF(ubyte lo @ Y, ubyte hi @ A) clobbers(A,X,Y)

extsub $c2ea = AYINT() clobbers(A,X,Y)          ; fac1-> signed word in ????? MSB FIRST. (might throw ILLEGAL QUANTITY) DON'T USE THIS, USE WRAPPER 'AYINT2' INSTEAD.

extsub $cbc2 = CONUPK(uword mflpt @ AY) clobbers(A,Y)       ; load mflpt value from memory  in A/Y into fac2
extsub $cd32 = MOVFA() clobbers(A,X)                        ; copy fac2 to fac1
extsub $cd0a = MOVMF(uword mflpt @ XY) clobbers(A,Y)        ; store fac1 to memory  X/Y as 5-byte mflpt

extsub $ccd8 = MOVFM(uword mflpt @ AY) clobbers(A,Y)        ; load mflpt value from memory  in A/Y into fac1
extsub $cd42 = MOVAF() clobbers(A,X)                        ; copy fac1 to fac2  (rounded the least significant bit)
extsub $cd45 = MOVEF() clobbers(A,X)                        ; copy fac1 to fac2

extsub $cb20 = LOG() clobbers(A,X,Y)                        ; fac1 = LN(fac1)  (natural log)
extsub $cb61 = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
extsub $cb5e = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt value from A/Y
extsub $cc48 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2/fac1  mind the order of the operands
extsub $cc45 = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1
extsub $cc18 = MUL10() clobbers(A,X,Y)                      ; fac1 *= 10
extsub $cc34 = DIV10() clobbers(A,X,Y)                      ; fac1 /= 10 , CAUTION: result is always positive! You have to fix sign manually!
extsub $c9a0 = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
extsub $c99d = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt value from A/Y
extsub $c97f = FADDH() clobbers(A,X,Y)                      ; fac1 += 0.5, for integer rounding- call this before INT
extsub $c989 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2-fac1   mind the order of the operands
extsub $c986 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1


extsub $ca0d = NORMAL() clobbers(A)                         ; normalize FAC1
extsub $cd61 = SIGN() -> ubyte @ A                          ; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
extsub $cd8e = ABS()                                        ; fac1 = ABS(fac1)
extsub $cdd1 = QINT() clobbers(A,X,Y)                       ; fac1 -> 4-byte signed integer in FAC, with the MSB FIRST.
extsub $ce02 = INT() clobbers(A,X,Y)                        ; INT() truncates, use FADDH first to integer round instead of trunc
extsub $cf93 = FOUT() clobbers(X) -> uword @ AY             ; fac1 -> string, address returned in AY

extsub $d108 = SQR() clobbers(A,X,Y)                        ; fac1 = SQRT(fac1)   TODO is this address correct?
extsub $d14b = NEGOP() clobbers(A)                          ; switch the sign of fac1 (fac1 = -fac1)
extsub $d184 = EXP() clobbers(A,X,Y)                        ; fac1 = EXP(fac1)  (e ** fac1)
extsub $d229 = RND() clobbers(A,X,Y)                        ; fac1 = RND(fac1) float random number generator
extsub $d282 = COS() clobbers(A,X,Y)                        ; fac1 = COS(fac1)
extsub $d289 = SIN() clobbers(A,X,Y)                        ; fac1 = SIN(fac1)
extsub $d2d2 = TAN() clobbers(A,X,Y)                        ; fac1 = TAN(fac1)
extsub $d32c = ATN() clobbers(A,X,Y)                        ; fac1 = ATN(fac1)

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

asmsub  GIVAYFAY  (uword value @ AY) clobbers(A,X,Y)  {
	; ---- signed 16 bit word in A/Y (lo/hi) to float in fac1
	%asm {{
		sta  P8ZP_SCRATCH_REG
		tya
		ldy  P8ZP_SCRATCH_REG
		jmp  GIVAYF		; this uses the inverse order, Y/A
	}}
}

%asminclude "library:c64/floats.asm"
%asminclude "library:c64/floats_funcs.asm"

}
