%import textio
%zeropage basicsafe

; Test for asr.w with a negative value.
;
; The m68k codegen emits the memory form `asr.w` directly on the regfile slot
; for `.w` count=1. On the 68000 (vamos) this is correct: $C000 -> $E000.
; On QEMU's 68020 model, `asr.w` with absolute addressing has an emulation
; bug: it zero-extends the 16-bit operand before the shift instead of
; sign-extending it, giving a logical shift result ($6000) for negative
; values. The 68020's `(a0)` and register forms are unaffected.
;
; The codegen works around this for the qemu68k target only: it emits
; `lea slot,a0; asr.w (a0)` instead of `asr.w slot`. See InstrBitwise.kt.
;
; Expected: $C000 ASR = $E000 (sign-extended), on both targets.
;
;   prog8c -target virtual  -emu examples/test.p8   ; PASS
;   prog8c -target amiga500 -emu examples/test.p8   ; PASS (vamos/68000)
;   prog8c -target qemu68k  -emu examples/test.p8   ; PASS (codegen workaround)

main {
    ubyte @shared pass_count = 0
    ubyte @shared fail_count = 0

    sub start() {
        test_asr_negative()
        test_asr_codegen()
        txt.nl()
        txt.print("summary: pass=")
        txt.print_ub(pass_count)
        txt.print(" fail=")
        txt.print_ub(fail_count)
        txt.nl()
    }

    sub check(bool ok) {
        if ok {
            pass_count++
            txt.print("PASS\n")
        } else {
            fail_count++
            txt.print("FAIL\n")
        }
    }

    ; The codegen path: `n = n >> 1` on a word variable. The compiler emits
    ; `asr.w` on the memory slot. On qemu68k it uses the (a0) workaround.
    sub test_asr_codegen() {
        txt.print("--- asr.w negative value, codegen path ---\n")
        word @shared n = -16384        ; $C000
        txt.print("before: n = ")
        txt.print_uwhex(n as uword, true)
        txt.print("  (expected $c000)\n")
        n = n >> 1
        txt.print("after:  n = ")
        txt.print_uwhex(n as uword, true)
        txt.print("  (expected $e000)\n")
        check(n == -8192)
    }

    ; The (a0) form via inline asm: confirms the addressing mode that the
    ; qemu68k codegen workaround uses also works correctly.
    sub test_asr_negative() {
        txt.print("--- asr.w negative value, (a0) form ---\n")
        word @shared n = 0
        %asm {{
            lea     p8b_main.p8s_test_asr_negative.p8v_n, a0
            move.w  #$C000, (a0)
            asr.w   (a0)
        }}
        txt.print("result: n = ")
        txt.print_uwhex(n as uword, true)
        txt.print("  (expected $e000)\n")
        check(n == -8192)
    }
}
