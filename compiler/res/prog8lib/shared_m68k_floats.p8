; Floating-point code requires a 68020+ CPU with an 68881/68882 FPU.
%option enable_floats, ignore_unused
floats {
    %option merge, no_symbol_prefixing, ignore_unused

    asmsub tostr(float value @FP0) -> str @A0 {
        %asm {{
            jmp floats._tostr
        }}
    }

    const float π = 3.141592653589793
    const float PI = π
    const float TWOPI = 2*π
    const float E = 2.718281828459045
    const float EPSILON = 4.9E-324

    asmsub sin(float angle @FP0) -> float @FP0 {
        %asm {{
            fsin.x fp0
            rts
        }}
    }

    asmsub cos(float angle @FP0) -> float @FP0 {
        %asm {{
            fcos.x fp0
            rts
        }}
    }

    asmsub tan(float value @FP0) -> float @FP0 {
        %asm {{
            ftan.x fp0
            rts
        }}
    }

    asmsub atan(float value @FP0) -> float @FP0 {
        %asm {{
            fatan.x fp0
            rts
        }}
    }

    asmsub ln(float value @FP0) -> float @FP0 {
        %asm {{
            flogn.x fp0
            rts
        }}
    }

    asmsub log2(float value @FP0) -> float @FP0 {
        %asm {{
            flog2.x fp0
            rts
        }}
    }

    asmsub round(float value @FP0) -> float @FP0 {
        %asm {{
            fint.x fp0
            rts
        }}
    }

    asmsub floor(float value @FP0) -> float @FP0 {
        %asm {{
            fintrz.x fp0,fp1
            fcmp.x fp0,fp1
            fbeq .floor_done
            ftst.x fp0
            fbge .floor_done
            fsub.s #1.0,fp1
.floor_done:
            fmove.x fp1,fp0
            rts
        }}
    }

    asmsub ceil(float value @FP0) -> float @FP0 {
        %asm {{
            fintrz.x fp0,fp1
            fcmp.x fp0,fp1
            fbeq .ceil_done
            ftst.x fp0
            fble .ceil_done
            fadd.s #1.0,fp1
.ceil_done:
            fmove.x fp1,fp0
            rts
        }}
    }

    asmsub pow(float value @FP0, float power @FP1) -> float @FP0 {
        %asm {{
            flogn.x fp0
            fmul.x fp1,fp0
            fetox.x fp0
            rts
        }}
    }

    sub atan2(float y, float x) -> float {
        float atn = atan(y / x)
        if x < 0
            atn += π
        if atn < 0
            atn += 2*π
        return atn
    }

    sub secant(float value) -> float { return 1.0 / cos(value) }
    sub csc(float value) -> float { return 1.0 / sin(value) }
    sub cot(float value) -> float { return 1.0 / tan(value) }

    sub rad(float angle) -> float {
        return angle * PI / 180.0
    }

    sub deg(float angle) -> float {
        return angle * 180.0 / PI
    }

    sub minf(float f1, float f2) -> float {
        if f1 < f2
            return f1
        return f2
    }

    sub maxf(float f1, float f2) -> float {
        if f1 > f2
            return f1
        return f2
    }

    sub clampf(float value, float minimum, float maximum) -> float {
        if value < minimum
            value = minimum
        if value < maximum
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
        return (1 - t) * v0 + t * v1
    }

    sub lerp_fast(float v0, float v1, float t) -> float {
        return v0 + t * (v1 - v0)
    }

    sub interpolate(float v, float inputMin, float inputMax, float outputMin, float outputMax) -> float {
        if outputMin == outputMax
            return outputMin
        v = (v - inputMin) / (inputMax - inputMin)
        return v * (outputMax - outputMin) + outputMin
    }

    asmsub rndseed(float seed @FP0) {
        %asm {{
            fmove.s fp0,floats._rnd_state
            rts
        }}
    }

    asmsub rnd() -> float @FP0 {
        %asm {{
            move.l floats._rnd_state,d0
            move.l #1103515245,d1
            mulu.l d1,d0
            add.l #12345,d0
            move.l d0,floats._rnd_state
            and.l #$ffffff,d0
            fmove.l d0,fp0
            fdiv.s floats._rnd_scale,fp0
            rts
        }}
    }

    asmsub parse(str value @A0) -> float @FP0 {
        %asm {{
            move.l a0,a1
            fmove.s floats._fzero,fp0
            fmove.s floats._fone,fp1
            moveq.l #0,d2
            moveq.l #0,d3
            moveq.l #0,d4
.parse_sign:
            move.b (a1),d0
            beq .parse_done
            cmp.b #' ',d0
            beq .parse_skip_space
            cmp.b #'+',d0
            beq .parse_after_sign
            cmp.b #'-',d0
            beq .parse_neg_sign
            bra .parse_int
.parse_skip_space:
            addq.l #1,a1
            bra .parse_sign
.parse_neg_sign:
            moveq.l #1,d2
            fmove.s floats._fneg_one,fp1
.parse_after_sign:
            addq.l #1,a1
            bra .parse_int
.parse_int:
            move.b (a1),d0
            beq .parse_done
            cmp.b #'.',d0
            beq .parse_frac
            cmp.b #'e',d0
            beq .parse_exp
            cmp.b #'E',d0
            beq .parse_exp
            cmp.b #'0',d0
            blo .parse_done
            cmp.b #'9',d0
            bhi .parse_done
            moveq.l #1,d4
            sub.b #'0',d0
            fmul.s floats._ften,fp0
            fmove.l d0,fp2
            fadd.x fp2,fp0
            addq.l #1,a1
            bra .parse_int
.parse_frac:
            addq.l #1,a1
            fmove.s floats._ften,fp2
.parse_frac_loop:
            move.b (a1),d0
            beq .parse_done
            cmp.b #'e',d0
            beq .parse_exp
            cmp.b #'E',d0
            beq .parse_exp
            cmp.b #'0',d0
            blo .parse_done
            cmp.b #'9',d0
            bhi .parse_done
            sub.b #'0',d0
            fmove.l d0,fp3
            fdiv.x fp2,fp3
            fadd.x fp3,fp0
            fmul.s floats._ften,fp2
            addq.l #1,a1
            bra .parse_frac_loop
.parse_exp:
            addq.l #1,a1
            move.b (a1),d0
            beq .parse_done
            cmp.b #'+',d0
            beq .parse_exp_after_sign
            cmp.b #'-',d0
            beq .parse_exp_neg
            bra .parse_exp_digit
.parse_exp_neg:
            moveq.l #1,d3
.parse_exp_after_sign:
            addq.l #1,a1
.parse_exp_digit:
            moveq.l #0,d5
.parse_exp_loop:
            move.b (a1),d0
            cmp.b #'0',d0
            blo .parse_apply_exp
            cmp.b #'9',d0
            bhi .parse_apply_exp
            sub.b #'0',d0
            mulu.l #10,d5
            add.l d0,d5
            addq.l #1,a1
            bra .parse_exp_loop
.parse_apply_exp:
            fmove.s floats._ften,fp2
            fmove.s floats._fone,fp3
.parse_exp_pow:
            tst.l d5
            beq .parse_exp_apply
            fmul.x fp2,fp3
            subq.l #1,d5
            bra .parse_exp_pow
.parse_exp_apply:
            tst.l d3
            beq .parse_exp_pos
            fdiv.x fp3,fp0
            bra .parse_exp_done
.parse_exp_pos:
            fmul.x fp3,fp0
.parse_exp_done:
.parse_done:
            fmul.x fp1,fp0
            rts
        }}
    }
}
