; Prog8 definitions for floating point handling on the VirtualMachine

%option enable_floats, ignore_unused

sys {
    %option merge       ; add some constants to sys

    const float  MAX_FLOAT = 1.7976931348623157e+308
    const float  MIN_FLOAT = -1.7976931348623157e+308
}

txt {
    %option merge       ; add function to txt

    alias print_f = floats.print
}


floats {

        const float  π         = 3.141592653589793
        const float  PI        = π
        const float  TWOPI     = 2*π
        const float  E         = 2.718281828459045
        const float  EPSILON   = 4.9E-324


sub print(float value) {
    ; ---- prints the floating point value (without a newline and no leading spaces).
    %ir {{
        loadm.f fr65535,floats.print.value
        syscall 15 (fr65535.f)
        return
    }}
}

sub tostr(float value) -> str {
    ; ---- converts the floating point value to a string (no leading spaces)
    str @shared buffer=" "*20
    %ir {{
        load.w r65535,floats.tostr.buffer
        loadm.f fr65535,floats.tostr.value
        syscall 34 (r65535.w, fr65535.f)
        load.w r0,floats.tostr.buffer
        returnr.w r0
    }}
}

sub parse(str value) -> float {
    ; -- parse a string value of a number to float
    %ir {{
        loadm.w  r65535,floats.parse.value
        syscall 32 (r65535.w): fr0.f
        returnr.f fr0
    }}
}

sub pow(float value, float power) -> float {
    %ir {{
        loadm.f fr0,floats.pow.value
        loadm.f fr1,floats.pow.power
        fpow.f fr0,fr1
        returnr.f fr0
    }}
}

sub sin(float angle) -> float {
    %ir {{
        loadm.f fr0,floats.sin.angle
        fsin.f fr0,fr0
        returnr.f fr0
    }}
}

sub cos(float angle) -> float {
    %ir {{
        loadm.f fr0,floats.cos.angle
        fcos.f fr0,fr0
        returnr.f fr0
    }}
}

sub tan(float value) -> float {
    %ir {{
        loadm.f fr0,floats.tan.value
        ftan.f fr0,fr0
        returnr.f fr0
    }}
}

sub atan(float value) -> float {
    %ir {{
        loadm.f fr0,floats.atan.value
        fatan.f fr0,fr0
        returnr.f fr0
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
sub csc(float value)    -> float { return 1.0 / sin(value) }
sub cot(float value)    -> float { return 1.0 / tan(value) }

sub ln(float value) -> float {
    %ir {{
        loadm.f fr0,floats.ln.value
        fln.f fr0,fr0
        returnr.f fr0
    }}
}

sub log2(float value) -> float {
    %ir {{
        loadm.f fr0,floats.log2.value
        flog.f fr0,fr0
        returnr.f fr0
    }}
}

sub rad(float angle) -> float {
    ; -- convert degrees to radians (d * pi / 180)
    return angle * PI / 180.0
}

sub deg(float angle) -> float {
    ; -- convert radians to degrees (d * (1/ pi * 180))
    return angle * 180.0 / PI
}

sub round(float value) -> float {
    %ir {{
        loadm.f fr0,floats.round.value
        fround.f fr0,fr0
        returnr.f fr0
    }}
}

sub floor(float value) -> float {
    %ir {{
        loadm.f fr0,floats.floor.value
        ffloor.f fr0,fr0
        returnr.f fr0
    }}
}

sub ceil(float value) -> float {
    ; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
    %ir {{
        loadm.f fr0,floats.ceil.value
        fceil.f fr0,fr0
        returnr.f fr0
    }}
}

sub rnd() -> float {
    %ir {{
        syscall 22 () : fr0.f
        returnr.f fr0
    }}
}

sub rndseed(float seed) {
    %ir {{
        loadm.f  fr65535,floats.rndseed.seed
        syscall 19 (fr65535.f)
        return
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
    if value<minimum
        value=minimum
    if value<maximum
        return value
    return maximum
}

sub normalize(float value) -> float {
    return value
}

sub push(float value) {
    ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
    %ir {{
        loadm.f  fr65535,floats.push.value
        push.f  fr65535
    }}
}

sub pop() -> float {
    ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
    %ir {{
        pop.f  fr65535
        returnr.f fr65535
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

}
