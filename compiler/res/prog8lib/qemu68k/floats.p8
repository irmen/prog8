%import shared_m68k_floats

sys {
    %option merge, ignore_unused
    const float MAX_FLOAT = 3.40282346e+38
    const float MIN_FLOAT = -3.40282346e+38
}

txt {
    %option merge, ignore_unused
    alias print_f = floats.print
}

math {
    %option merge, ignore_unused
    alias lerpf = floats.lerp
    alias lerpf_fast = floats.lerp_fast
}

floats {
    %option no_symbol_prefixing, ignore_unused
    %asminclude "library:shared_m68k_floats.asm"

    asmsub print(float value @FP0) {
        %asm {{
            jsr floats._tostr
.loop:
            move.b (a0)+,d0
            beq .done
            jsr qemu.chrout
            bra .loop
.done:
            rts
        }}
    }
}
