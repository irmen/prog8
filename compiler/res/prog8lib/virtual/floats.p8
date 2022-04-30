; Prog8 definitions for floating point handling on the VirtualMachine
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%option enable_floats

floats {

    const float  PI     = 3.141592653589793
    const float  TWOPI  = 6.283185307179586

sub print_f(float value) {
    ; ---- prints the floating point value (without a newline).
    %asm {{
        loadm.f fr0,{floats.print_f.value}
        syscall 35
        return
    }}
}

sub pow(float value, float power) -> float {
    ; TODO fpow.f instruction
    %asm {{
        loadm.f fr0,{floats.pow.value}
        loadm.f fr1,{floats.pow.power}
        fpow.f fr0,fr0
        return
    }}
}

sub fabs(float value) -> float {
    ; TODO fabs.f instruction
    %asm {{
        loadm.f fr0,{floats.fabs.value}
        fabs.f fr0,fr0
        return
    }}
}

sub sin(float angle) -> float {
    ; TODO fsin.f instruction
    %asm {{
        loadm.f fr0,{floats.sin.angle}
        fsin.f fr0,fr0
        return
    }}
}

sub cos(float angle) -> float {
    ; TODO fcos.f instruction
    %asm {{
        loadm.f fr0,{floats.cos.angle}
        fcos.f fr0,fr0
        return
    }}
}

sub tan(float value) -> float {
    ; TODO ftan.f instruction
    %asm {{
        loadm.f fr0,{floats.tan.value}
        ftan.f fr0,fr0
        return
    }}
}

sub atan(float value) -> float {
    ; TODO fatan.f instruction
    %asm {{
        loadm.f fr0,{floats.atan.value}
        fatan.f fr0,fr0
        return
    }}
}

sub ln(float value) -> float {
    ; TODO fln.f instruction
    %asm {{
        loadm.f fr0,{floats.ln.value}
        fln.f fr0,fr0
        return
    }}
}

sub log2(float value) -> float {
    ; TODO flog2.f instruction
    %asm {{
        loadm.f fr0,{floats.log2.value}
        flog2.f fr0,fr0
        return
    }}
}

sub sqrt(float value) -> float {
    ; TODO fsqrt.f instruction
    %asm {{
        loadm.f fr0,{floats.sqrt.value}
        fsqrt.f fr0,fr0
        return
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
    ; TODO fround.f instruction
    %asm {{
        loadm.f fr0,{floats.round.value}
        fround.f fr0,fr0
        return
    }}
}

sub floor(float value) -> float {
    ; TODO ffloor.f instruction
    %asm {{
        loadm.f fr0,{floats.floor.value}
        ffloor.f fr0,fr0
        return
    }}
}

sub ceil(float value) -> float {
    ; -- ceil: tr = int(f); if tr==f -> return  else return tr+1
    ; TODO fceil.f instruction
    %asm {{
        loadm.f fr0,{floats.ceil.value}
        fceil.f fr0,fr0
        return
    }}
}

sub rndf() -> float {
    ; TODO frnd.f instruction
    %asm {{
        frnd.f fr0
        return
    }}
}
}
