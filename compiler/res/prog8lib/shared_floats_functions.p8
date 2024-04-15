floats {
    ; the floating point functions shared across compiler targets
    %option merge, no_symbol_prefixing, ignore_unused

    const float  π      = 3.141592653589793
    const float  PI     = π
    const float  TWOPI  = 2*π


asmsub print(float value @FAC1) clobbers(A,X,Y) {
	; ---- prints the floating point value (without a newline). No leading space (unlike BASIC)!
	%asm {{
	    jsr  tostr
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		jsr  cbm.CHROUT
		iny
		bne  -
+		rts
	}}
}

asmsub tostr(float value @FAC1) clobbers(X) -> str @AY {
    ; ---- converts the floating point value to a string. No leading space!
    %asm {{
        jsr  FOUT
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #' '
		bne  +
		inc  P8ZP_SCRATCH_W1
		bne  +
		inc  P8ZP_SCRATCH_W1+1
+		lda  P8ZP_SCRATCH_W1
		ldy  P8ZP_SCRATCH_W1+1
		rts
    }}
}

sub pow(float value, float power) -> float {
    %asm {{
        stx  P8ZP_SCRATCH_W1
        sty  P8ZP_SCRATCH_W1+1
        lda  #<value
        ldy  #>value
        jsr  floats.CONUPK
        lda  #<power
        ldy  #>power
        jsr  floats.FPWR
        ldx  P8ZP_SCRATCH_W1
        ldy  P8ZP_SCRATCH_W1+1
        rts
    }}
}

sub sin(float angle) -> float {
    %asm {{
        lda  #<angle
        ldy  #>angle
        jsr  MOVFM
        jmp  SIN
    }}
}

sub cos(float angle) -> float {
    %asm {{
        lda  #<angle
        ldy  #>angle
        jsr  MOVFM
        jmp  COS
        rts
    }}
}

sub tan(float value) -> float {
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        jmp  TAN
    }}
}

sub atan(float value) -> float {
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        jmp  ATN
    }}
}

; two-argument arctangent that returns an angle in the correct quadrant
; for the signs of x and y, normalized to the range [0, 2π]
sub atan2(float y, float x) -> float {
    float atn = atan(y / x)
        if x < 0 atn += π
        if atn < 0 atn += 2*π
        return atn
    }

; reciprocal functions
sub secant(float value) -> float { return 1.0 / cos(value) }
sub csc(float value) -> float { return 1.0 / sin(value) }
sub cot(float value) -> float { return 1.0 / tan(value) }

sub ln(float value) -> float {
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        jmp  LOG
    }}
}

sub log2(float value) -> float {
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        jsr  LOG
        jsr  MOVEF
        lda  #<FL_LOG2_const
        ldy  #>FL_LOG2_const
        jsr  MOVFM
        jmp  FDIVT
    }}
}

sub rad(float angle) -> float {
    ; -- convert degrees to radians (d * pi / 180)
    %asm {{
        lda  #<angle
        ldy  #>angle
        jsr  MOVFM
        lda  #<_pi_div_180
        ldy  #>_pi_div_180
        jmp  FMULT
_pi_div_180	.byte 123, 14, 250, 53, 18		; pi / 180
    }}
}

sub deg(float angle) -> float {
    ; -- convert radians to degrees (d * (1/ pi * 180))
    %asm {{
        lda  #<angle
        ldy  #>angle
        jsr  MOVFM
        lda  #<_one_over_pi_div_180
        ldy  #>_one_over_pi_div_180
        jmp  FMULT
        rts
_one_over_pi_div_180	.byte 134, 101, 46, 224, 211		; 1 / (pi * 180)
    }}
}

sub round(float value) -> float {
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        jsr  FADDH
        jmp  INT
    }}
}

sub floor(float value) -> float {
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        jmp  INT
    }}
}

sub ceil(float value) -> float {
    ; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
    %asm {{
        lda  #<value
        ldy  #>value
        jsr  MOVFM
        ldx  #<fmath_float1
        ldy  #>fmath_float1
        jsr  MOVMF
        jsr  INT
        lda  #<fmath_float1
        ldy  #>fmath_float1
        jsr  FCOMP
        cmp  #0
        beq  +
        lda  #<FL_ONE_const
        ldy  #>FL_ONE_const
        jsr  FADD
+       rts
    }}
}

sub rndseed(float seed) {
    if seed>0
        seed = -seed    ; make sure fp seed is always negative

    %asm {{
        lda  #<seed
        ldy  #>seed
        jsr  MOVFM		; load float into fac1
        lda  #-1
        jmp  floats.RND
    }}
}


sub minf(float f1, float f2) -> float {
    if f1<f2
        return f1
    return f2
}


sub maxf(float f1, float f2) -> float {
    if f1>f2
        return f1
    return f2
}


sub clampf(float value, float minimum, float maximum) -> float {
    if value>maximum
      value=maximum
    if value>minimum
      return value
    return minimum
}

inline asmsub push(float value @FAC1) {
    %asm {{
        jsr  floats.pushFAC1
    }}
}

inline asmsub pop() -> float @FAC1 {
    %asm {{
        clc
        jsr  floats.popFAC
    }}
}

}
