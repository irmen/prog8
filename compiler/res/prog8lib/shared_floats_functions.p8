
sys {
    %option merge, ignore_unused       ; add some constants to sys

    const float  MAX_FLOAT = 1.7014118345e+38         ; bytes: 255,127,255,255,255
    const float  MIN_FLOAT = -1.7014118345e+38        ; bytes: 255,255,255,255,255
}

txt {
    %option merge, ignore_unused       ; add function to txt

    alias print_f = floats.print
}

math {
    %option merge, ignore_unused       ; add functions to math

    alias lerpf = floats.lerp
    alias lerpf_fast = floats.lerp_fast
    alias interpolatef = floats.interpolate
}

floats {
    ; the floating point functions shared across compiler targets
    %option merge, no_symbol_prefixing, ignore_unused

    const float  π         = 3.141592653589793
    const float  PI        = π
    const float  TWOPI     = 2*π
    const float  E         = 2.718281828459045
    const float  EPSILON   = 2.938735878e-39          ; bytes: 1,0,0,0,0


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
    float atn
    if x == 0 {
        atn = π/2
        if y == 0 return 0
        if y < 0 {
            atn += π
        }
    } else {
        atn = atan(y / x)
    }
    if x < 0 atn += π
    if atn < 0 atn += 2*π
    return atn
}

; reciprocal functions
sub secant(float value) -> float { return 1.0 / cos(value) }
sub csc(float value)    -> float { return 1.0 / sin(value) }
sub cot(float value)    -> float { return 1.0 / tan(value) }

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

FL_LOG2_const	.byte  $80, $31, $72, $17, $f8	; log(2)
        ; !notreached!
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

sub lerp(float v0, float v1, float t) -> float {
    ; Linear interpolation (LERP)
    ; Precise method, which guarantees v = v1 when t = 1.
    ; returns an interpolation between two inputs (v0, v1) for a parameter t in the closed unit interval [0, 1]
    return (1 - t) * v0 + t * v1
}

sub lerp_fast(float v0, float v1, float t) -> float {
    ; Linear interpolation (LERP)
    ; Imprecise (but slightly faster) method, which does not guarantee v = v1 when t = 1
    ; returns an interpolation between two inputs (v0, v1) for a parameter t in the closed unit interval [0, 1]
    return v0 + t * (v1 - v0)
}

sub interpolate(float v, float inputMin, float inputMax, float outputMin, float outputMax) -> float {
    ; Interpolate a value v in interval [inputMin, inputMax] to output interval [outputMin, outputMax]
    if outputMin==outputMax
        return outputMin
    v = (v - inputMin) / (inputMax - inputMin)
    return v * (outputMax - outputMin) + outputMin
}

    asmsub internal_long_AY_to_FAC() {
        ; Used by the compiler to cast a long to a a float in FAC
        ; convert the long pointed to by AY into a float and store it in FAC
        %asm {{
            sta  cx16.r1L
            sty  cx16.r1H
            lda  #<floats_temp_var
            ldy  #>floats_temp_var
            jsr  internal_long_R1_to_float_AY
            lda  #<floats_temp_var
            ldy  #>floats_temp_var
            jmp  MOVFM
        }}
    }

    sub internal_long_R1_to_float_AY() {
        ; Used by the compiler to cast a long to a a float variable:
        ; make the float pointed to by AY equal to the long pointed to by R1  (as float)
        %asm {{
            sta  cx16.r0L
            sty  cx16.r0H
        }}

        long value = peekl(cx16.r1)
        ^^ubyte @zp vptr = &value

        if value==0 {
            sys.memset(cx16.r0, sizeof(float), 0)
            return
        }

        ; Determine the sign and work with the absolute value
        bool is_negative = value < 0
        if is_negative
            value = -value

        ; Calculate the exponent by finding the highest set bit
        ubyte highest_bit_pos = internal_get_vptr_highest_bit_pos(vptr)
        ; For the normalized mantissa, the highest bit is an implicit 1 followed by the rest
        ; So we shift the absolute value to get the proper mantissa representation
        value <<= 31 - highest_bit_pos

        ; Store the exponent
        cx16.r0[0] = highest_bit_pos + 129

        ; Store the mantissa with the sign bit
        cx16.r0[1] = vptr[3] & $7f
        if is_negative
            cx16.r0[1] |= $80
        cx16.r0[2] = vptr[2]
        cx16.r0[3] = vptr[1]
        cx16.r0[4] = vptr[0]
        return
    }

    sub internal_get_vptr_highest_bit_pos(^^ubyte vptr) -> ubyte {
        ; note: separate subroutine not nested in internal_long_R1_to_float_AY so that 64tass can optimize it out if not used
        if vptr[3]==0
            if vptr[2]==0
                if vptr[1]==0
                    if vptr[0]==0 return 0
                    else return highest_bit_in_byte(vptr[0])
                else return 8+highest_bit_in_byte(vptr[1])
            else return 16+highest_bit_in_byte(vptr[2])
        else return 24+highest_bit_in_byte(vptr[3])
    }

    asmsub highest_bit_in_byte(ubyte value @A) -> ubyte @Y {
        ; note: separate subroutine not nested in internal_get_vptr_highest_bit_pos so that 64tass can optimize it out if not used
        %asm {{
            ldy  #0
-           lsr  a
            beq  +
            iny
            bne  -
+           rts
        }}
    }
}
