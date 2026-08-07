; Prog8 definitions for floating point handling on the m68k target (68881/68882 FPU)

%option enable_floats, ignore_unused

sys {
    %option merge, ignore_unused       ; add constants to sys

    const float  MAX_FLOAT = 3.40282346e+38
    const float  MIN_FLOAT = -3.40282346e+38
}

txt {
    %option merge, ignore_unused       ; add function to txt

    alias print_f = floats.print
}

math {
    %option merge, ignore_unused       ; add functions to math

    alias lerpf = floats.lerp
    alias lerpf_fast = floats.lerp_fast
}


floats {
    %option no_symbol_prefixing, ignore_unused

    const float  π         = 3.141592653589793
    const float  PI        = π
    const float  TWOPI     = 2*π
    const float  E         = 2.718281828459045
    const float  EPSILON   = 4.9E-324

    %asminclude "library:qemu68k/floats.asm"

    asmsub tostr(float value @FP0) -> str @A0 {
        %asm {{
            jmp floats._tostr
        }}
    }

    asmsub print(float value @FP0) {
        ; floats.print implementation
        ; Input: FP0 = float value
        ; Clobbers: D0, A1, FP0-FP1
        %asm {{
  jsr  floats._tostr

.loop:
  move.b  (a0)+,d0
  beq  .done
  jsr  qemu.chrout
  bra  .loop

.done:
  rts
        }}
    }

    ; === Direct FPU operations (single argument, in-place) ===

    asmsub sin(float angle @FP0) -> float @FP0 {
        %asm {{
            fsin.x  fp0
            rts
        }}
    }

    asmsub cos(float angle @FP0) -> float @FP0 {
        %asm {{
            fcos.x  fp0
            rts
        }}
    }

    asmsub tan(float value @FP0) -> float @FP0 {
        %asm {{
            ftan.x  fp0
            rts
        }}
    }

    asmsub atan(float value @FP0) -> float @FP0 {
        %asm {{
            fatan.x  fp0
            rts
        }}
    }

    asmsub ln(float value @FP0) -> float @FP0 {
        %asm {{
            flogn.x  fp0
            rts
        }}
    }

    asmsub log2(float value @FP0) -> float @FP0 {
        %asm {{
            flog2.x  fp0
            rts
        }}
    }

    asmsub round(float value @FP0) -> float @FP0 {
        ; round to nearest integer (per FPU rounding mode)
        %asm {{
            fint.x  fp0
            rts
        }}
    }

    asmsub floor(float value @FP0) -> float @FP0 {
        ; floor(x): truncate toward zero, subtract 1 if x < 0 and not integer
        %asm {{
            fintrz.x fp0,fp1        ; fp1 = truncate toward zero
            fcmp.x  fp0,fp1
            fbeq  .floor_done        ; equal => x was integer
            ftst.x  fp0
            fbge  .floor_done        ; x >= 0 => truncated is floor
            fsub.s #1.0,fp1          ; x < 0 and not integer => subtract 1
.floor_done:
            fmove.x fp1,fp0
            rts
        }}
    }

    asmsub ceil(float value @FP0) -> float @FP0 {
        ; ceil(x): truncate toward zero, add 1 if x > 0 and not integer
        %asm {{
            fintrz.x fp0,fp1        ; fp1 = truncate toward zero
            fcmp.x  fp0,fp1
            fbeq  .ceil_done         ; equal => x was integer
            ftst.x  fp0
            fble  .ceil_done         ; x <= 0 => truncated is ceil
            fadd.s #1.0,fp1         ; x > 0 and not integer => add 1
.ceil_done:
            fmove.x fp1,fp0
            rts
        }}
    }

    ; === Power (two arguments) ===

    asmsub pow(float value @FP0, float power @FP1) -> float @FP0 {
        ; value ^ power = e^(power * ln(value))
        %asm {{
            flogn.x  fp0             ; fp0 = ln(value)
            fmul.x  fp1,fp0         ; fp0 = ln(value) * power
            fetox.x  fp0             ; fp0 = e^(...) = value^power
            rts
        }}
    }

    ; === Compound operations (Prog8 source using the above) ===

    sub atan2(float y, float x) -> float {
        float atn = atan(y / x)
        if x < 0 atn += π
        if atn < 0 atn += 2*π
        return atn
    }

    sub secant(float value) -> float { return 1.0 / cos(value) }
    sub csc(float value)    -> float { return 1.0 / sin(value) }
    sub cot(float value)    -> float { return 1.0 / tan(value) }

    sub rad(float angle) -> float {
        ; convert degrees to radians (d * pi / 180)
        return angle * PI / 180.0
    }

    sub deg(float angle) -> float {
        ; convert radians to degrees (d * (1 / pi * 180))
        return angle * 180.0 / PI
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
        if value<minimum
            value=minimum
        if value<maximum
            return value
        return maximum
    }

    sub mod(float value, float divisor) -> float {
        return value - floor(value / divisor) * divisor
    }

    sub normalize(float value) -> float {
        return value
    }

    sub lerp(float v0, float v1, float t) -> float {
        ; Linear interpolation (LERP)
        ; Precise method, which guarantees v = v1 when t = 1.
        return (1 - t) * v0 + t * v1
    }

    sub lerp_fast(float v0, float v1, float t) -> float {
        ; Linear interpolation (LERP)
        ; Imprecise (but slightly faster) method, which does not guarantee v = v1 when t = 1
        return v0 + t * (v1 - v0)
    }

    sub interpolate(float v, float inputMin, float inputMax, float outputMin, float outputMax) -> float {
        ; Interpolate a value v in interval [inputMin, inputMax] to output interval [outputMin, outputMax]
        if outputMin==outputMax
            return outputMin
        v = (v - inputMin) / (inputMax - inputMin)
        return v * (outputMax - outputMin) + outputMin
    }

    ; === Random number generation ===

    asmsub rndseed(float seed @FP0) {
        ; store the seed state for the PRNG
        %asm {{
            fmove.s fp0,floats._rnd_state
            rts
        }}
    }

    asmsub rnd() -> float @FP0 {
        ; Linear congruential generator producing a float in [0, 1).
        ; state = state * 1103515245 + 12345  (classic glibc LCG constants)
        ; result = (state mod 2^24) as float / 2^24
        %asm {{
            move.l  floats._rnd_state,d0
            move.l  #1103515245,d1
            mulu.l  d1,d0
            add.l   #12345,d0
            move.l  d0,floats._rnd_state
            and.l   #$ffffff,d0        ; keep low 24 bits
            fmove.l d0,fp0
            fdiv.s  floats._rnd_scale,fp0   ; divide by 2^24
            rts
        }}
    }

    ; === String parsing ===

    asmsub parse(str value @A0) -> float @FP0 {
        ; parse a string into a float. Handles optional sign, integer part,
        ; optional fractional part, and optional exponent (e/E notation).
        %asm {{
            move.l  a0,a1              ; a1 = cursor
            fmove.s floats._fzero,fp0  ; result accumulator = 0.0
            fmove.s floats._fone,fp1   ; sign = +1.0
            moveq.l #0,d2              ; sign flag: 0 = positive, 1 = negative
            moveq.l #0,d3              ; exponent sign
            moveq.l #0,d4              ; have seen digit flag

.parse_sign:
            move.b  (a1),d0
            beq  .parse_done
            cmp.b   #' ',d0
            beq  .parse_skip_space
            cmp.b   #'+',d0
            beq  .parse_after_sign
            cmp.b   #'-',d0
            beq  .parse_neg_sign
            bra  .parse_int

.parse_skip_space:
            addq.l  #1,a1
            bra  .parse_sign

.parse_neg_sign:
            moveq.l #1,d2
            fmove.s floats._fneg_one,fp1

.parse_after_sign:
            addq.l  #1,a1
            bra  .parse_int

            ; === integer part ===
.parse_int:
            move.b  (a1),d0
            beq  .parse_done
            cmp.b   #'.',d0
            beq  .parse_frac
            cmp.b   #'e',d0
            beq  .parse_exp
            cmp.b   #'E',d0
            beq  .parse_exp
            cmp.b   #'0',d0
            blo  .parse_done
            cmp.b   #'9',d0
            bhi  .parse_done

            moveq.l #1,d4              ; saw a digit
            sub.b   #'0',d0
            fmul.s  floats._ften,fp0  ; fp0 = fp0 * 10
            fmove.l d0,fp2
            fadd.x  fp2,fp0            ; fp0 = fp0 + digit
            addq.l  #1,a1
            bra  .parse_int

            ; === fractional part ===
.parse_frac:
            addq.l  #1,a1
            fmove.s floats._ften,fp2  ; fp2 = 10.0 (for division scale)
.parse_frac_loop:
            move.b  (a1),d0
            beq  .parse_done
            cmp.b   #'e',d0
            beq  .parse_exp
            cmp.b   #'E',d0
            beq  .parse_exp
            cmp.b   #'0',d0
            blo  .parse_done
            cmp.b   #'9',d0
            bhi  .parse_done

            sub.b   #'0',d0
            fmove.l d0,fp3
            fdiv.x  fp2,fp3            ; fp3 = digit / scale
            fadd.x  fp3,fp0            ; fp0 = fp0 + digit/scale
            fmul.s  floats._ften,fp2  ; scale *= 10
            addq.l  #1,a1
            bra  .parse_frac_loop

            ; === exponent ===
.parse_exp:
            addq.l  #1,a1
            move.b  (a1),d0
            beq  .parse_done
            cmp.b   #'+',d0
            beq  .parse_exp_after_sign
            cmp.b   #'-',d0
            beq  .parse_exp_neg
            bra  .parse_exp_digit

.parse_exp_neg:
            moveq.l #1,d3
.parse_exp_after_sign:
            addq.l  #1,a1

.parse_exp_digit:
            moveq.l #0,d5             ; exponent value
.parse_exp_loop:
            move.b  (a1),d0
            cmp.b   #'0',d0
            blo  .parse_apply_exp
            cmp.b   #'9',d0
            bhi  .parse_apply_exp
            sub.b   #'0',d0
            mulu.l  #10,d5
            add.l   d0,d5
            addq.l  #1,a1
            bra  .parse_exp_loop

.parse_apply_exp:
            ; compute 10^exponent and multiply/divide
            fmove.s floats._ften,fp2
            fmove.s floats._fone,fp3
.parse_exp_pow:
            tst.l   d5
            beq  .parse_exp_apply
            fmul.x  fp2,fp3
            subq.l  #1,d5
            bra  .parse_exp_pow
.parse_exp_apply:
            tst.l   d3
            beq  .parse_exp_pos
            fdiv.x  fp3,fp0           ; divide by 10^exp
            bra  .parse_exp_done
.parse_exp_pos:
            fmul.x  fp3,fp0           ; multiply by 10^exp
.parse_exp_done:

.parse_done:
            ; apply sign
            fmul.x  fp1,fp0
            rts
        }}
    }
}
