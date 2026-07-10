; Prog8 definitions for floating point handling on the m68k target (68881/68882 FPU)

%option enable_floats, ignore_unused

txt {
    %option merge, ignore_unused       ; add function to txt

    alias print_f = floats.print
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
}
