%import textio

; Tests bit shift operators that emit the suboptimal LOAD #N + LSLN pattern.
; Currently the IR codegen for `x << N` does:
;   LOAD rN, #N
;   LSLN rX, rN
; which materializes the count into a register. The recommended fix
; (m68k-optimizations-review.md, section 3) is to add LSLI/LSRI/ASRI
; opcodes with a literal count, so the IR builder emits
;   LSLI rX, #N
; directly. The m68k backend then emits `lsl.b #N, d0` (single
; instruction, 6+2N cycles) for N=1..8 and `lsl.b #8, d0;
; lsl.b #(N-8), d0` (2 instructions) for N=9..15 on a word. The 6502
; backend unrolls to N `asl`/`lsr` instructions.
;
; With ubyte/uword x = 5:
;   x << 1  =  10   x >> 1  =  2
;   x << 2  =  20   x >> 2  =  1
;   x << 3  =  40   x >> 3  =  0
;   x << 8  =   0   x >> 8  =  0    (ubyte, 8 bits)
;   x << 8  =1280   x >> 8  =  0    (uword/long, >8 bits)
;   x << 33 =   0   x >> 33 =  0    (compiler folds: result is 0 for ubyte)
;
;   prog8c -target qemu68k -out /tmp/opencode examples/test.p8
;   rg -E 'lsli|lsri|asri|load.*#|lsln|lsrn|asrn' /tmp/opencode/test.p8ir
;   prog8c -target qemu68k -emu examples/test.p8

main {
    sub start() {
        test_ubyte_shifts()
        test_uword_shifts()
        test_long_shifts()
    }

    sub test_ubyte_shifts() {
        txt.print("=== ubyte shifts (x=5) ===")
        txt.nl()
        ubyte @shared x = 5
        ubyte @shared lsl1  = x << 1
        ubyte @shared lsl2  = x << 2
        ubyte @shared lsl3  = x << 3
        ubyte @shared lsl8  = x << 8
        ubyte @shared lsl33 = x << 33
        ubyte @shared lsr1  = x >> 1
        ubyte @shared lsr2  = x >> 2
        ubyte @shared lsr3  = x >> 3
        ubyte @shared lsr8  = x >> 8
        ubyte @shared lsr33 = x >> 33

        txt.print("x        = ")
        txt.print_ub(x)
        txt.print(" (expected  5)")
        txt.nl()
        txt.print("x << 1   = ")
        txt.print_ub(lsl1)
        txt.print(" (expected 10)")
        txt.nl()
        txt.print("x << 2   = ")
        txt.print_ub(lsl2)
        txt.print(" (expected 20)")
        txt.nl()
        txt.print("x << 3   = ")
        txt.print_ub(lsl3)
        txt.print(" (expected 40)")
        txt.nl()
        txt.print("x << 8   = ")
        txt.print_ub(lsl8)
        txt.print(" (expected  0)")
        txt.nl()
        txt.print("x << 33  = ")
        txt.print_ub(lsl33)
        txt.print(" (expected  0)")
        txt.nl()
        txt.print("x >> 1   = ")
        txt.print_ub(lsr1)
        txt.print(" (expected  2)")
        txt.nl()
        txt.print("x >> 2   = ")
        txt.print_ub(lsr2)
        txt.print(" (expected  1)")
        txt.nl()
        txt.print("x >> 3   = ")
        txt.print_ub(lsr3)
        txt.print(" (expected  0)")
        txt.nl()
        txt.print("x >> 8   = ")
        txt.print_ub(lsr8)
        txt.print(" (expected  0)")
        txt.nl()
        txt.print("x >> 33  = ")
        txt.print_ub(lsr33)
        txt.print(" (expected  0)")
        txt.nl()
    }

    sub test_uword_shifts() {
        txt.print("=== uword shifts (x=5) ===")
        txt.nl()
        uword @shared x = 5
        uword @shared lsl1  = x << 1
        uword @shared lsl2  = x << 2
        uword @shared lsl3  = x << 3
        uword @shared lsl8  = x << 8
        uword @shared lsr1  = x >> 1
        uword @shared lsr2  = x >> 2
        uword @shared lsr3  = x >> 3
        uword @shared lsr8  = x >> 8

        txt.print("x        = ")
        txt.print_uw(x)
        txt.print(" (expected     5)")
        txt.nl()
        txt.print("x << 1   = ")
        txt.print_uw(lsl1)
        txt.print(" (expected    10)")
        txt.nl()
        txt.print("x << 2   = ")
        txt.print_uw(lsl2)
        txt.print(" (expected    20)")
        txt.nl()
        txt.print("x << 3   = ")
        txt.print_uw(lsl3)
        txt.print(" (expected    40)")
        txt.nl()
        txt.print("x << 8   = ")
        txt.print_uw(lsl8)
        txt.print(" (expected  1280)")
        txt.nl()
        txt.print("x >> 1   = ")
        txt.print_uw(lsr1)
        txt.print(" (expected     2)")
        txt.nl()
        txt.print("x >> 2   = ")
        txt.print_uw(lsr2)
        txt.print(" (expected     1)")
        txt.nl()
        txt.print("x >> 3   = ")
        txt.print_uw(lsr3)
        txt.print(" (expected     0)")
        txt.nl()
        txt.print("x >> 8   = ")
        txt.print_uw(lsr8)
        txt.print(" (expected     0)")
        txt.nl()
    }

    sub test_long_shifts() {
        txt.print("=== long shifts (x=5) ===")
        txt.nl()
        long @shared x = 5
        long @shared lsl1  = x << 1
        long @shared lsl2  = x << 2
        long @shared lsl3  = x << 3
        long @shared lsl8  = x << 8
        long @shared lsr1  = x >> 1
        long @shared lsr2  = x >> 2
        long @shared lsr3  = x >> 3
        long @shared lsr8  = x >> 8

        txt.print("x        = ")
        txt.print_l(x)
        txt.print(" (expected     5)")
        txt.nl()
        txt.print("x << 1   = ")
        txt.print_l(lsl1)
        txt.print(" (expected    10)")
        txt.nl()
        txt.print("x << 2   = ")
        txt.print_l(lsl2)
        txt.print(" (expected    20)")
        txt.nl()
        txt.print("x << 3   = ")
        txt.print_l(lsl3)
        txt.print(" (expected    40)")
        txt.nl()
        txt.print("x << 8   = ")
        txt.print_l(lsl8)
        txt.print(" (expected  1280)")
        txt.nl()
        txt.print("x >> 1   = ")
        txt.print_l(lsr1)
        txt.print(" (expected     2)")
        txt.nl()
        txt.print("x >> 2   = ")
        txt.print_l(lsr2)
        txt.print(" (expected     1)")
        txt.nl()
        txt.print("x >> 3   = ")
        txt.print_l(lsr3)
        txt.print(" (expected     0)")
        txt.nl()
        txt.print("x >> 8   = ")
        txt.print_l(lsr8)
        txt.print(" (expected     0)")
        txt.nl()

        ; signed right shift on a negative value (>> on a signed
        ; type lowers to ASR in the IR, not LSR).
        txt.print("--- long signed right shift (>> y=-5) ---")
        txt.nl()
        long @shared y = -5
        long @shared asr1  = y >> 1
        long @shared asr2  = y >> 2
        long @shared asr3  = y >> 3
        long @shared asr8  = y >> 8

        txt.print("y        = ")
        txt.print_l(y)
        txt.print(" (expected    -5)")
        txt.nl()
        txt.print("y >> 1   = ")
        txt.print_l(asr1)
        txt.print(" (expected    -3)")
        txt.nl()
        txt.print("y >> 2   = ")
        txt.print_l(asr2)
        txt.print(" (expected    -2)")
        txt.nl()
        txt.print("y >> 3   = ")
        txt.print_l(asr3)
        txt.print(" (expected    -1)")
        txt.nl()
        txt.print("y >> 8   = ")
        txt.print_l(asr8)
        txt.print(" (expected    -1)")
        txt.nl()
    }
}
