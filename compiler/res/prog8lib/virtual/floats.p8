; Prog8 definitions for floating point handling on the VirtualMachine
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%option enable_floats

floats {

    const float  PI     = 3.141592653589793
    const float  TWOPI  = 6.283185307179586

sub print_f(float value) {
    ; ---- prints the floating point value (without a newline).
    void syscall1fp(sys.SC_PRINTF, value)
}

sub pow(float value, float power) -> float {
    ; TODO
    return -42.42
}

sub fabs(float value) -> float {
    ; TODO
    return -42.42
}

sub sin(float angle) -> float {
    ; TODO
    return -42.42
}

sub cos(float angle) -> float {
    ; TODO
    return -42.42
}

sub tan(float value) -> float {
    ; TODO
    return -42.42
}

sub atan(float value) -> float {
    ; TODO
    return -42.42
}

sub ln(float value) -> float {
    ; TODO
    return -42.42
}

sub log2(float value) -> float {
    ; TODO
    return -42.42
}

sub sqrt(float value) -> float {
    ; TODO
    return -42.42
}

sub rad(float angle) -> float {
    ; -- convert degrees to radians (d * pi / 180)
    ; TODO
    return -42.42
}

sub deg(float angle) -> float {
    ; -- convert radians to degrees (d * (1/ pi * 180))
    ; TODO
    return -42.42
}

sub round(float value) -> float {
    ; TODO
    return -42.42
}

sub floor(float value) -> float {
    ; TODO
    return -42.42
}

sub ceil(float value) -> float {
    ; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
    ; TODO
    return -42.42
}

sub rndf() -> float {
    ; TODO
    return -42.42
}
}
